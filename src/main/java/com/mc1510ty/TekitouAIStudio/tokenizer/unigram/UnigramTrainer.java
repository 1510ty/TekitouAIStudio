package com.mc1510ty.TekitouAIStudio.tokenizer.unigram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Streaming Unigram trainer. It never stores the full corpus in memory. */
final class UnigramTrainer {
    private final Unigram.Config config;

    UnigramTrainer(Unigram.Config config) {
        this.config = Objects.requireNonNull(config, "config").validate();
    }

    UnigramModel train(Unigram.CorpusSource corpus) throws IOException {
        Objects.requireNonNull(corpus, "corpus");

        long[] byteCounts = new long[Unigram.BYTE_VOCAB_SIZE];
        long[] totals = new long[2]; // [0] byte count, [1] code-point count
        CandidateCounter candidateCounter = new CandidateCounter(config.maxCandidates);

        // A piece may cross a core boundary. The source supplies left/right
        // context, while only the non-overlapping core contributes statistics.
        forEachWindow(corpus, (window, coreStart, coreEnd) -> {
            String coreText = substringByCodePoints(window, coreStart, coreEnd);
            byte[] coreBytes = coreText.getBytes(StandardCharsets.UTF_8);
            totals[0] += coreBytes.length;
            for (byte value : coreBytes) {
                byteCounts[value & 0xFF]++;
            }

            int[] codePoints = window.codePoints().toArray();
            totals[1] += coreEnd - coreStart;
            collectCandidates(codePoints, coreStart, coreEnd, candidateCounter);
        });

        if (totals[0] == 0 || totals[1] == 0) {
            throw new IllegalArgumentException("Training corpus is empty");
        }

        // Pass 2: get exact frequencies for only the bounded candidate shortlist.
        List<CandidateCounter.Entry> shortlist = candidateCounter.snapshot();
        Map<String, Integer> shortlistIndexes = new HashMap<>(capacityFor(shortlist.size()));
        for (int i = 0; i < shortlist.size(); i++) {
            shortlistIndexes.put(shortlist.get(i).piece, i);
        }
        long[] exactCandidateCounts = new long[shortlist.size()];
        forEachWindow(corpus, (window, coreStart, coreEnd) -> countShortlistedCandidates(
                window.codePoints().toArray(), coreStart, coreEnd,
                shortlistIndexes, exactCandidateCounts));

        List<UnigramCandidate> candidates = selectInitialCandidates(
                shortlist, exactCandidateCounts);
        int targetPieceCount = config.vocabularySize - Unigram.FIRST_PIECE_ID;

        // Initialize byte fallback and learned-piece scores.
        double[] scores = initializeScores(
                candidates, byteCounts, totals[0], totals[1]);

        // Each EM iteration rereads the corpus. Only one bounded chunk is resident at a time.
        for (int iteration = 0; iteration < config.emIterations; iteration++) {
            UnigramTrie trie = UnigramMath.buildTrie(candidates);
            double[] expectedCounts = expectationStep(corpus, trie, scores);
            scores = normalizeExpectedCounts(expectedCounts);

            if (candidates.size() > targetPieceCount) {
                int gradualKeepCount = (int) Math.ceil(
                        candidates.size() * (1.0 - config.pruneFraction));
                int keepCount = Math.max(targetPieceCount, gradualKeepCount);
                keepCount = Math.min(keepCount, candidates.size() - 1);

                List<UnigramCandidate> previousCandidates = candidates;
                candidates = pruneCandidates(previousCandidates, expectedCounts, keepCount);
                scores = scoresForPrunedVocabulary(
                        candidates, previousCandidates, expectedCounts);
            }
        }

        // Ensure the final vocabulary does not exceed the requested size.
        if (candidates.size() > targetPieceCount) {
            UnigramTrie trie = UnigramMath.buildTrie(candidates);
            double[] pruningCounts = expectationStep(corpus, trie, scores);
            List<UnigramCandidate> previousCandidates = candidates;
            candidates = pruneCandidates(previousCandidates, pruningCounts, targetPieceCount);
            scores = scoresForPrunedVocabulary(candidates, previousCandidates, pruningCounts);
        }

        // Final E/M pass so the scores match the final vocabulary.
        UnigramTrie finalTrie = UnigramMath.buildTrie(candidates);
        double[] finalExpectedCounts = expectationStep(corpus, finalTrie, scores);
        scores = normalizeExpectedCounts(finalExpectedCounts);

        return new UnigramModel(candidates, scores);
    }

    private void forEachWindow(Unigram.CorpusSource corpus,
                               Unigram.WindowConsumer consumer) throws IOException {
        int overlap = config.maxPieceCodePoints - 1;
        corpus.forEachWindow(config.maxSentenceCodePoints, overlap, consumer);
    }

    private void collectCandidates(int[] codePoints, int coreStart, int coreEnd,
                                   CandidateCounter counts) {
        int maxLength = config.maxPieceCodePoints;
        // Only starts in the core are counted. The right overlap lets a piece
        // beginning near the core's end extend across the artificial boundary.
        for (int start = coreStart; start < coreEnd; start++) {
            StringBuilder builder = new StringBuilder();
            int limit = Math.min(codePoints.length, start + maxLength);
            for (int end = start; end < limit; end++) {
                builder.appendCodePoint(codePoints[end]);
                String piece = builder.toString();
                if (end > start || codePoints[start] > 0x7F) {
                    counts.add(piece);
                }
            }
        }
    }

    private void countShortlistedCandidates(int[] codePoints,
                                            int coreStart,
                                            int coreEnd,
                                            Map<String, Integer> indexes,
                                            long[] exactCounts) {
        int maxLength = config.maxPieceCodePoints;
        for (int start = coreStart; start < coreEnd; start++) {
            StringBuilder builder = new StringBuilder();
            int limit = Math.min(codePoints.length, start + maxLength);
            for (int end = start; end < limit; end++) {
                builder.appendCodePoint(codePoints[end]);
                String piece = builder.toString();
                if (end > start || codePoints[start] > 0x7F) {
                    Integer index = indexes.get(piece);
                    if (index != null) {
                        exactCounts[index]++;
                    }
                }
            }
        }
    }

    private static String substringByCodePoints(String text, int start, int end) {
        int startChar = text.offsetByCodePoints(0, start);
        int endChar = text.offsetByCodePoints(0, end);
        return text.substring(startChar, endChar);
    }

    private List<UnigramCandidate> selectInitialCandidates(
            List<CandidateCounter.Entry> shortlist,
            long[] exactCounts) {
        List<UnigramCandidate> result = new ArrayList<>(shortlist.size());
        for (int i = 0; i < shortlist.size(); i++) {
            long frequency = exactCounts[i];
            if (frequency >= config.minPieceFrequency) {
                String text = shortlist.get(i).piece;
                result.add(new UnigramCandidate(
                        text, text.getBytes(StandardCharsets.UTF_8), frequency));
            }
        }

        result.sort(Comparator
                .comparingLong((UnigramCandidate candidate) -> candidate.frequency).reversed()
                .thenComparingInt(candidate -> candidate.bytes.length)
                .thenComparing(candidate -> candidate.text));
        return result;
    }

    private double[] initializeScores(List<UnigramCandidate> candidates,
                                      long[] byteCounts,
                                      long totalBytes,
                                      long totalCodePoints) {
        double[] scores = new double[Unigram.FIRST_PIECE_ID + candidates.size()];
        // EOS is a structural token, not a candidate in the Unigram segmentation model.
        scores[Unigram.EOS_ID] = Double.NEGATIVE_INFINITY;
        double denominator = totalBytes + config.smoothing * Unigram.BYTE_VOCAB_SIZE;
        for (int i = 0; i < Unigram.BYTE_VOCAB_SIZE; i++) {
            scores[i] = Math.log((byteCounts[i] + config.smoothing) / denominator);
        }

        for (int i = 0; i < candidates.size(); i++) {
            double probability = (candidates.get(i).frequency + config.smoothing)
                    / (totalCodePoints + config.smoothing);
            scores[Unigram.FIRST_PIECE_ID + i] = Math.log(Math.max(probability, 1.0e-300));
        }
        return scores;
    }

    private double[] expectationStep(Unigram.CorpusSource corpus,
                                     UnigramTrie trie,
                                     double[] scores) throws IOException {
        double[] expectedCounts = new double[scores.length];

        forEachWindow(corpus, (window, coreStart, coreEnd) -> {
            int[] codePoints = window.codePoints().toArray();
            byte[] bytes = window.getBytes(StandardCharsets.UTF_8);
            int coreStartByte = utf8ByteOffset(codePoints, coreStart);
            int coreEndByte = utf8ByteOffset(codePoints, coreEnd);

            // The overlap provides context to the local forward/backward pass.
            // Expected counts are accumulated only for token starts owned by
            // the core, preventing overlap regions from being counted twice.
            UnigramEdge[][] lattice = UnigramMath.buildLatticeArray(bytes, trie, scores);
            int length = bytes.length;

            double[] forward = new double[length + 1];
            double[] backward = new double[length + 1];
            Arrays.fill(forward, Double.NEGATIVE_INFINITY);
            Arrays.fill(backward, Double.NEGATIVE_INFINITY);
            forward[0] = 0.0;

            for (int position = 0; position < length; position++) {
                if (forward[position] == Double.NEGATIVE_INFINITY) {
                    continue;
                }
                for (UnigramEdge edge : lattice[position]) {
                    forward[edge.end] = UnigramMath.logAdd(
                            forward[edge.end], forward[position] + scores[edge.tokenId]);
                }
            }

            backward[length] = 0.0;
            for (int position = length - 1; position >= 0; position--) {
                double sum = Double.NEGATIVE_INFINITY;
                for (UnigramEdge edge : lattice[position]) {
                    sum = UnigramMath.logAdd(
                            sum, scores[edge.tokenId] + backward[edge.end]);
                }
                backward[position] = sum;
            }

            double logZ = forward[length];
            if (!Double.isFinite(logZ)) {
                throw new IllegalStateException(
                        "No valid segmentation for a training sequence");
            }

            for (int position = coreStartByte; position < coreEndByte; position++) {
                if (forward[position] == Double.NEGATIVE_INFINITY) {
                    continue;
                }
                for (UnigramEdge edge : lattice[position]) {
                    double logPosterior = forward[position]
                            + scores[edge.tokenId]
                            + backward[edge.end] - logZ;
                    if (logPosterior > -745.0) {
                        expectedCounts[edge.tokenId] += Math.exp(logPosterior);
                    }
                }
            }
        });
        return expectedCounts;
    }

    private static int utf8ByteOffset(int[] codePoints, int codePointOffset) {
        int byteOffset = 0;
        for (int i = 0; i < codePointOffset; i++) {
            int cp = codePoints[i];
            if (cp <= 0x7F) {
                byteOffset++;
            } else if (cp <= 0x7FF) {
                byteOffset += 2;
            } else if (cp <= 0xFFFF) {
                byteOffset += 3;
            } else {
                byteOffset += 4;
            }
        }
        return byteOffset;
    }

    private double[] normalizeExpectedCounts(double[] expectedCounts) {
        double total = 0.0;
        // Byte fallback tokens and learned pieces participate in the Unigram
        // distribution. EOS is intentionally excluded from that distribution.
        for (int i = 0; i < Unigram.BYTE_VOCAB_SIZE; i++) {
            total += expectedCounts[i] + config.smoothing;
        }
        for (int i = Unigram.FIRST_PIECE_ID; i < expectedCounts.length; i++) {
            total += expectedCounts[i] + config.smoothing;
        }

        double[] scores = new double[expectedCounts.length];
        scores[Unigram.EOS_ID] = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < Unigram.BYTE_VOCAB_SIZE; i++) {
            scores[i] = Math.log((expectedCounts[i] + config.smoothing) / total);
        }
        for (int i = Unigram.FIRST_PIECE_ID; i < expectedCounts.length; i++) {
            scores[i] = Math.log((expectedCounts[i] + config.smoothing) / total);
        }
        return scores;
    }

    private List<UnigramCandidate> pruneCandidates(List<UnigramCandidate> candidates,
                                                   double[] expectedCounts,
                                                   int keepCount) {
        List<Integer> order = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator
                .comparingDouble((Integer index) -> expectedCounts[Unigram.FIRST_PIECE_ID + index])
                .reversed()
                .thenComparing(index -> candidates.get(index).text));

        boolean[] keep = new boolean[candidates.size()];
        for (int i = 0; i < keepCount; i++) {
            keep[order.get(i)] = true;
        }

        List<UnigramCandidate> result = new ArrayList<>(keepCount);
        for (int i = 0; i < candidates.size(); i++) {
            if (keep[i]) {
                result.add(candidates.get(i));
            }
        }
        return result;
    }

    private double[] scoresForPrunedVocabulary(List<UnigramCandidate> retainedCandidates,
                                               List<UnigramCandidate> previousCandidates,
                                               double[] oldExpectedCounts) {
        double[] counts = new double[Unigram.FIRST_PIECE_ID + retainedCandidates.size()];
        System.arraycopy(oldExpectedCounts, 0, counts, 0, Unigram.BYTE_VOCAB_SIZE);

        Map<String, Double> expectedByPiece = new HashMap<>(capacityFor(previousCandidates.size()));
        for (int i = 0; i < previousCandidates.size(); i++) {
            expectedByPiece.put(previousCandidates.get(i).text,
                    oldExpectedCounts[Unigram.FIRST_PIECE_ID + i]);
        }
        for (int i = 0; i < retainedCandidates.size(); i++) {
            Double value = expectedByPiece.get(retainedCandidates.get(i).text);
            counts[Unigram.FIRST_PIECE_ID + i] = value == null ? 0.0 : value;
        }
        return normalizeExpectedCounts(counts);
    }

    private static int capacityFor(int expectedSize) {
        if (expectedSize < 3) {
            return 4;
        }
        return (int) Math.min(1_000_000_000L,
                Math.ceil(expectedSize / 0.75d) + 1L);
    }

    /**
     * Bounded-memory Space-Saving counter. It keeps approximate heavy hitters;
     * a second corpus pass computes exact counts for the retained shortlist.
     */
    private static final class CandidateCounter {
        private final int capacity;
        private final Map<String, Long> counts;
        private final NavigableMap<Long, Set<String>> buckets = new TreeMap<>();

        CandidateCounter(int capacity) {
            this.capacity = capacity;
            this.counts = new HashMap<>(capacityFor(capacity));
        }

        void add(String piece) {
            Long current = counts.get(piece);
            if (current != null) {
                removeFromBucket(piece, current);
                long updated = current + 1L;
                counts.put(piece, updated);
                buckets.computeIfAbsent(updated, ignored -> new LinkedHashSet<>()).add(piece);
                return;
            }

            if (counts.size() < capacity) {
                counts.put(piece, 1L);
                buckets.computeIfAbsent(1L, ignored -> new LinkedHashSet<>()).add(piece);
                return;
            }

            Map.Entry<Long, Set<String>> minimum = buckets.firstEntry();
            long minimumCount = minimum.getKey();
            String victim = minimum.getValue().iterator().next();
            removeFromBucket(victim, minimumCount);
            counts.remove(victim);

            long replacementCount = minimumCount + 1L;
            counts.put(piece, replacementCount);
            buckets.computeIfAbsent(replacementCount, ignored -> new LinkedHashSet<>()).add(piece);
        }

        List<Entry> snapshot() {
            List<Entry> result = new ArrayList<>(counts.size());
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                result.add(new Entry(entry.getKey(), entry.getValue()));
            }
            result.sort(Comparator.comparingLong((Entry entry) -> entry.estimatedCount)
                    .reversed().thenComparing(entry -> entry.piece));
            return result;
        }

        private void removeFromBucket(String piece, long count) {
            Set<String> bucket = buckets.get(count);
            if (bucket == null) {
                throw new IllegalStateException("Candidate counter bucket is inconsistent");
            }
            bucket.remove(piece);
            if (bucket.isEmpty()) {
                buckets.remove(count);
            }
        }

        private static final class Entry {
            final String piece;
            final long estimatedCount;

            Entry(String piece, long estimatedCount) {
                this.piece = piece;
                this.estimatedCount = estimatedCount;
            }
        }
    }
}
