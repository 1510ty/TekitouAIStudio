package com.mc1510ty.TekitouAIStudio.tokenizer.unigram;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Public API for the Unigram tokenizer. */
public final class Unigram {
    /** IDs 0..255 represent raw UTF-8 bytes. */
    public static final int BYTE_VOCAB_SIZE = 256;

    /** Dedicated end-of-sequence token. Normal text encoding never emits this ID. */
    public static final int EOS_ID = BYTE_VOCAB_SIZE;
    public static final String EOS_TOKEN = "<|eos|>";

    /** First ID available to learned Unigram pieces. */
    public static final int FIRST_PIECE_ID = EOS_ID + 1;

    private Unigram() {
    }

    /**
     * A repeatable source of training sequences.
     *
     * The source is called multiple times: once to collect corpus statistics,
     * once to recount shortlisted candidates, and again for every EM pass.
     * Implementations must therefore replay the same content on every call.
     */
    @FunctionalInterface
    public interface CorpusSource {
        void forEachSentence(SentenceConsumer consumer) throws IOException;

        /**
         * Supplies bounded overlapping windows. The core range is owned by this
         * window; overlap is context only and must not be counted twice.
         *
         * The default implementation windows each supplied sentence independently.
         * Streaming sources may override this method to preserve context across
         * their own I/O chunk boundaries.
         */
        default void forEachWindow(int coreCodePoints, int overlapCodePoints,
                                   WindowConsumer consumer) throws IOException {
            if (coreCodePoints < 1 || overlapCodePoints < 0) {
                throw new IllegalArgumentException("Invalid window sizes");
            }
            Objects.requireNonNull(consumer, "consumer");
            forEachSentence(sentence -> {
                if (sentence != null && !sentence.isEmpty()) {
                    emitStringWindows(sentence, coreCodePoints, overlapCodePoints, consumer);
                }
            });
        }
    }

    /** Consumer for an overlapping window and its non-overlapping core range. */
    @FunctionalInterface
    public interface WindowConsumer {
        /** coreStart/coreEnd are code-point offsets into window; coreEnd is exclusive. */
        void accept(String window, int coreStart, int coreEnd) throws IOException;
    }

    /** Consumer used by {@link CorpusSource}. */
    @FunctionalInterface
    public interface SentenceConsumer {
        void accept(String sentence) throws IOException;
    }

    /** Training settings. Vocabulary size includes byte fallback, EOS, and learned pieces. */
    public static final class Config {
        public int vocabularySize = 32_000;
        public int maxPieceCodePoints = 8;
        public long minPieceFrequency = 2;

        /** Maximum number of distinct substring candidates kept during the first pass. */
        public int maxCandidates = 100_000;

        /**
         * Maximum number of owned Unicode code points per training window core.
         * Windows automatically include maxPieceCodePoints - 1 context code
         * points on each side, so learned pieces can cross artificial boundaries.
         */
        public int maxSentenceCodePoints = 4_096;

        public int emIterations = 8;
        public double pruneFraction = 0.20;
        public double smoothing = 1.0e-8;

        public Config validate() {
            if (vocabularySize < FIRST_PIECE_ID) {
                throw new IllegalArgumentException(
                        "vocabularySize must be at least " + FIRST_PIECE_ID
                                + " (256 byte tokens + EOS)");
            }
            if (maxPieceCodePoints < 1) {
                throw new IllegalArgumentException("maxPieceCodePoints must be >= 1");
            }
            if (minPieceFrequency < 1) {
                throw new IllegalArgumentException("minPieceFrequency must be >= 1");
            }
            if (maxCandidates < 1) {
                throw new IllegalArgumentException("maxCandidates must be >= 1");
            }
            if (maxSentenceCodePoints < maxPieceCodePoints) {
                throw new IllegalArgumentException(
                        "maxSentenceCodePoints must be >= maxPieceCodePoints");
            }
            if (emIterations < 1) {
                throw new IllegalArgumentException("emIterations must be >= 1");
            }
            if (!(pruneFraction > 0.0 && pruneFraction < 1.0)) {
                throw new IllegalArgumentException("pruneFraction must be between 0 and 1");
            }
            if (!(smoothing > 0.0) || !Double.isFinite(smoothing)) {
                throw new IllegalArgumentException("smoothing must be finite and > 0");
            }
            return this;
        }
    }

    /** Trained tokenizer. Keeps the original Unigram.Model API. */
    public static final class Model extends UnigramModel {
        private Model(UnigramModel model) {
            super(model);
        }

        public static Model load(Path path) throws IOException {
            return new Model(UnigramModel.loadInternal(path));
        }
    }

    /** Trainer facade. */
    public static final class Trainer {
        private final Unigram.Config config;
        private final UnigramTrainer trainer;

        public Trainer(Config config) {
            this.config = Objects.requireNonNull(config, "config").validate();
            this.trainer = new UnigramTrainer(this.config);
        }

        /** Trains from an in-memory list. The list is not copied. */
        public Model train(List<String> corpus) {
            Objects.requireNonNull(corpus, "corpus");
            CorpusSource source = consumer -> {
                for (String sentence : corpus) {
                    consumer.accept(sentence);
                }
            };
            try {
                return new Model(trainer.train(source));
            } catch (IOException impossible) {
                throw new UncheckedIOException(impossible);
            }
        }

        /** Trains from a repeatable streaming source. */
        public Model train(CorpusSource source) throws IOException {
            return new Model(trainer.train(Objects.requireNonNull(source, "source")));
        }

        /** Streams a UTF-8 text file from disk instead of loading it into a List. */
        public Model train(Path textFile) throws IOException {
            return train(fromTextFile(textFile, StandardCharsets.UTF_8,
                    config.maxSentenceCodePoints));
        }

        /** Streams a text file using the specified charset. */
        public Model train(Path textFile, Charset charset) throws IOException {
            return train(fromTextFile(textFile, charset, config.maxSentenceCodePoints));
        }
    }

    /**
     * Creates a repeatable streaming source for a text file.
     *
     * During training, the file is reopened for every pass. The specialized
     * window implementation uses a rolling look-ahead buffer so learned pieces
     * can cross artificial I/O chunk boundaries without duplicating statistics.
     * The core size is bounded by maxChunkCodePoints (and by the trainer's
     * configured maxSentenceCodePoints).
     */
    public static CorpusSource fromTextFile(Path path, Charset charset,
                                            int maxChunkCodePoints) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(charset, "charset");
        if (maxChunkCodePoints < 1) {
            throw new IllegalArgumentException("maxChunkCodePoints must be >= 1");
        }

        return new CorpusSource() {
            @Override
            public void forEachSentence(SentenceConsumer consumer) throws IOException {
                Objects.requireNonNull(consumer, "consumer");
                streamFileWindows(path, charset, maxChunkCodePoints, 0,
                        (window, coreStart, coreEnd) -> consumer.accept(window));
            }

            @Override
            public void forEachWindow(int coreCodePoints, int overlapCodePoints,
                                      WindowConsumer consumer) throws IOException {
                Objects.requireNonNull(consumer, "consumer");
                int effectiveCore = Math.min(coreCodePoints, maxChunkCodePoints);
                if (effectiveCore <= overlapCodePoints) {
                    throw new IllegalArgumentException(
                            "maxChunkCodePoints must be greater than the required overlap ("
                                    + overlapCodePoints + ")");
                }
                streamFileWindows(path, charset, effectiveCore, overlapCodePoints, consumer);
            }
        };
    }

    private static void emitStringWindows(String sentence, int coreCodePoints,
                                          int overlapCodePoints,
                                          WindowConsumer consumer) throws IOException {
        int[] codePoints = sentence.codePoints().toArray();
        int coreStart = 0;
        while (coreStart < codePoints.length) {
            int coreEnd = Math.min(codePoints.length, coreStart + coreCodePoints);
            int windowStart = Math.max(0, coreStart - overlapCodePoints);
            int windowEnd = Math.min(codePoints.length, coreEnd + overlapCodePoints);
            int[] windowPoints = java.util.Arrays.copyOfRange(
                    codePoints, windowStart, windowEnd);
            String window = new String(windowPoints, 0, windowPoints.length);
            consumer.accept(window, coreStart - windowStart, coreEnd - windowStart);
            coreStart = coreEnd;
        }
    }

    /**
     * Reads the file as one logical character stream while retaining only a
     * core window plus left/right context. The right context is re-used as the
     * beginning of the next core, so every code point is owned exactly once.
     */
    private static void streamFileWindows(Path path, Charset charset,
                                          int coreCodePoints, int overlapCodePoints,
                                          WindowConsumer consumer) throws IOException {
        if (coreCodePoints < 1 || overlapCodePoints < 0
                || (overlapCodePoints > 0 && coreCodePoints <= overlapCodePoints)) {
            throw new IllegalArgumentException("Invalid streaming window sizes");
        }

        try (PushbackReader reader = new PushbackReader(
                new BufferedReader(Files.newBufferedReader(path, charset)), 1)) {
            int[] leftContext = new int[0];
            int[] pendingCorePrefix = new int[0];
            boolean eof = false;

            while (true) {
                IntAccumulator core = new IntAccumulator(coreCodePoints);
                core.addAll(pendingCorePrefix);

                while (core.size() < coreCodePoints && !eof) {
                    int codePoint = readCodePoint(reader);
                    if (codePoint < 0) {
                        eof = true;
                    } else {
                        core.add(codePoint);
                    }
                }

                if (core.size() == 0) {
                    break;
                }

                IntAccumulator right = new IntAccumulator(overlapCodePoints);
                while (right.size() < overlapCodePoints && !eof) {
                    int codePoint = readCodePoint(reader);
                    if (codePoint < 0) {
                        eof = true;
                    } else {
                        right.add(codePoint);
                    }
                }

                int[] corePoints = core.toArray();
                int[] rightPoints = right.toArray();
                int[] windowPoints = concat(leftContext, corePoints, rightPoints);
                String window = new String(windowPoints, 0, windowPoints.length);
                consumer.accept(window, leftContext.length,
                        leftContext.length + corePoints.length);

                leftContext = suffix(corePoints, overlapCodePoints);
                pendingCorePrefix = rightPoints;

                if (eof && pendingCorePrefix.length == 0) {
                    break;
                }
            }
        }
    }

    private static int readCodePoint(PushbackReader reader) throws IOException {
        int first = reader.read();
        if (first < 0) {
            return -1;
        }
        char firstChar = (char) first;
        if (!Character.isHighSurrogate(firstChar)) {
            return first;
        }

        int next = reader.read();
        if (next >= 0 && Character.isLowSurrogate((char) next)) {
            return Character.toCodePoint(firstChar, (char) next);
        }
        if (next >= 0) {
            reader.unread(next);
        }
        return first;
    }

    private static int[] concat(int[] first, int[] second, int[] third) {
        int[] result = new int[first.length + second.length + third.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        System.arraycopy(third, 0, result, first.length + second.length, third.length);
        return result;
    }

    private static int[] suffix(int[] values, int maximumLength) {
        int length = Math.min(values.length, maximumLength);
        return java.util.Arrays.copyOfRange(values, values.length - length, values.length);
    }

    private static final class IntAccumulator {
        private final int[] values;
        private int size;

        IntAccumulator(int capacity) {
            values = new int[Math.max(1, capacity)];
        }

        void add(int value) {
            values[size++] = value;
        }

        void addAll(int[] source) {
            if (source.length > values.length - size) {
                throw new IllegalArgumentException("Pending context exceeds core window size");
            }
            System.arraycopy(source, 0, values, size, source.length);
            size += source.length;
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(values, size);
        }
    }

    public static CorpusSource fromTextFile(Path path, int maxChunkCodePoints) {
        return fromTextFile(path, StandardCharsets.UTF_8, maxChunkCodePoints);
    }

    public static CorpusSource fromTextFile(Path path) {
        return fromTextFile(path, StandardCharsets.UTF_8, 4_096);
    }

    public static Model train(List<String> corpus, Config config) {
        return new Trainer(config).train(corpus);
    }

    public static Model train(CorpusSource source, Config config) throws IOException {
        return new Trainer(config).train(source);
    }

    public static Model train(Path textFile, Config config) throws IOException {
        return new Trainer(config).train(textFile);
    }

    public static Model train(Path textFile, Charset charset, Config config) throws IOException {
        return new Trainer(config).train(textFile, charset);
    }

    public static Model train(List<String> corpus, int vocabularySize) {
        Config config = new Config();
        config.vocabularySize = vocabularySize;
        return train(corpus, config);
    }
}
