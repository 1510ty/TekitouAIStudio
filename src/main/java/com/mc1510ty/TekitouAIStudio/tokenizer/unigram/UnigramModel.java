package com.mc1510ty.TekitouAIStudio.tokenizer.unigram;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

class UnigramModel {
private static final int MODEL_MAGIC = 0x54414955; // "TAIU"
private static final int MODEL_VERSION = 2;
private static final int MAX_MODEL_PIECES = 2_000_000;
private static final int MAX_PIECE_BYTES = 1_000_000;


    private final String[] pieces;
    private final byte[][] pieceBytes;
    private final double[] logScores;
    private final UnigramTrie trie;

    UnigramModel(List<UnigramCandidate> candidates, double[] scores) {
        this.pieces = new String[candidates.size()];
        this.pieceBytes = new byte[candidates.size()][];
        this.logScores = Arrays.copyOf(scores, scores.length);

        for (int i = 0; i < candidates.size(); i++) {
            UnigramCandidate candidate = candidates.get(i);
            pieces[i] = candidate.text;
            pieceBytes[i] = Arrays.copyOf(candidate.bytes, candidate.bytes.length);
        }
        this.trie = UnigramMath.buildTrie(pieceBytes);
    }

    UnigramModel(String[] pieces, byte[][] pieceBytes, double[] logScores) {
        this.pieces = pieces;
        this.pieceBytes = pieceBytes;
        this.logScores = logScores;
        this.trie = UnigramMath.buildTrie(pieceBytes);
    }

    UnigramModel(UnigramModel other) {
        this.pieces = other.pieces;
        this.pieceBytes = other.pieceBytes;
        this.logScores = other.logScores;
        this.trie = other.trie;
    }

    /** Total vocabulary size, including 256 byte tokens, EOS, and learned pieces. */
    public int vocabularySize() {
        return Unigram.FIRST_PIECE_ID + pieces.length;
    }

    public int learnedPieceCount() {
        return pieces.length;
    }

    /**
     * Returns a human-readable representation of a token ID.
     * Byte fallback tokens are displayed as <0xXX>.
     */
    public String getPiece(int tokenId) {
        checkTokenId(tokenId);
        if (tokenId < Unigram.BYTE_VOCAB_SIZE) {
            return String.format("<0x%02X>", tokenId);
        }
        if (tokenId == Unigram.EOS_ID) {
            return Unigram.EOS_TOKEN;
        }
        return pieces[tokenId - Unigram.FIRST_PIECE_ID];
    }

    /**
     * Returns the segmentation log-score. EOS has no Unigram segmentation
     * probability and therefore returns negative infinity.
     */
    public double getLogScore(int tokenId) {
        checkTokenId(tokenId);
        if (tokenId == Unigram.EOS_ID) {
            return Double.NEGATIVE_INFINITY;
        }
        return logScores[tokenId];
    }

    /** Appends EOS to an already encoded sequence without modifying the input array. */
    public int[] appendEos(int[] tokenIds) {
        Objects.requireNonNull(tokenIds, "tokenIds");
        for (int tokenId : tokenIds) {
            checkTokenId(tokenId);
        }
        if (tokenIds.length > 0 && tokenIds[tokenIds.length - 1] == Unigram.EOS_ID) {
            return Arrays.copyOf(tokenIds, tokenIds.length);
        }
        int[] result = Arrays.copyOf(tokenIds, tokenIds.length + 1);
        result[tokenIds.length] = Unigram.EOS_ID;
        return result;
    }

    /** Encodes ordinary text and appends the dedicated EOS token. */
    public int[] encodeWithEos(String text) {
        return appendEos(encode(text));
    }

    /**
     * Encodes text using the highest-scoring Unigram segmentation.
     * The result can always be decoded back to the original UTF-8 bytes.
     */
    public int[] encode(String text) {
        Objects.requireNonNull(text, "text");
        byte[] input = text.getBytes(StandardCharsets.UTF_8);
        if (input.length == 0) {
            return new int[0];
        }

        List<UnigramEdge>[] lattice = UnigramMath.buildLattice(input, trie, logScores);
        int length = input.length;
        double[] best = new double[length + 1];
        int[] previousPosition = new int[length + 1];
        int[] previousToken = new int[length + 1];
        Arrays.fill(best, Double.NEGATIVE_INFINITY);
        Arrays.fill(previousToken, -1);
        best[0] = 0.0;

        for (int position = 0; position < length; position++) {
            if (best[position] == Double.NEGATIVE_INFINITY) {
                continue;
            }
            for (UnigramEdge edge : lattice[position]) {
                double score = best[position] + logScores[edge.tokenId];
                if (score > best[edge.end]) {
                    best[edge.end] = score;
                    previousPosition[edge.end] = position;
                    previousToken[edge.end] = edge.tokenId;
                }
            }
        }

        return UnigramMath.reconstruct(previousPosition, previousToken, length);
    }

    /**
     * Samples a segmentation instead of always taking the single best path.
     * temperature=1.0 uses the model distribution; lower values are sharper.
     */
    public int[] encodeSampled(String text, Random random, double temperature) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(random, "random");
        if (!(temperature > 0.0) || !Double.isFinite(temperature)) {
            throw new IllegalArgumentException("temperature must be finite and > 0");
        }

        byte[] input = text.getBytes(StandardCharsets.UTF_8);
        if (input.length == 0) {
            return new int[0];
        }

        double[] scaledScores = new double[logScores.length];
        for (int i = 0; i < logScores.length; i++) {
            scaledScores[i] = logScores[i] / temperature;
        }

        List<UnigramEdge>[] lattice = UnigramMath.buildLattice(input, trie, scaledScores);
        int length = input.length;
        double[] backward = new double[length + 1];
        Arrays.fill(backward, Double.NEGATIVE_INFINITY);
        backward[length] = 0.0;

        for (int position = length - 1; position >= 0; position--) {
            double total = Double.NEGATIVE_INFINITY;
            for (UnigramEdge edge : lattice[position]) {
                total = UnigramMath.logAdd(total,
                        scaledScores[edge.tokenId] + backward[edge.end]);
            }
            backward[position] = total;
        }

        List<Integer> result = new ArrayList<>();
        int position = 0;
        while (position < length) {
            double denominator = backward[position];
            double randomValue = random.nextDouble();
            double cumulative = 0.0;
            UnigramEdge selected = null;
            UnigramEdge last = null;

            for (UnigramEdge edge : lattice[position]) {
                double logWeight = scaledScores[edge.tokenId]
                        + backward[edge.end] - denominator;
                double weight = Math.exp(logWeight);
                cumulative += weight;
                last = edge;
                if (randomValue <= cumulative) {
                    selected = edge;
                    break;
                }
            }

            if (selected == null) {
                selected = last; // floating-point rounding fallback
            }
            if (selected == null) {
                throw new IllegalStateException("No token path at byte position " + position);
            }

            result.add(selected.tokenId);
            position = selected.end;
        }

        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Decodes ordinary token IDs to text. EOS is structural metadata and is
     * skipped, so decode(encodeWithEos(text)) returns the original text.
     */
    public String decode(int[] tokenIds) {
        Objects.requireNonNull(tokenIds, "tokenIds");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int tokenId : tokenIds) {
            checkTokenId(tokenId);
            if (tokenId < Unigram.BYTE_VOCAB_SIZE) {
                output.write(tokenId);
            } else if (tokenId == Unigram.EOS_ID) {
                // EOS is structural metadata, not text. Skip it when decoding text.
                continue;
            } else {
                output.writeBytes(pieceBytes[tokenId - Unigram.FIRST_PIECE_ID]);
            }
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    /** Saves the tokenizer model in a compact binary format. */
    public void save(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(MODEL_MAGIC);
            out.writeInt(MODEL_VERSION);
            out.writeInt(pieces.length);

            for (int i = 0; i < Unigram.BYTE_VOCAB_SIZE; i++) {
                out.writeDouble(logScores[i]);
            }

            for (int i = 0; i < pieces.length; i++) {
                byte[] bytes = pieceBytes[i];
                out.writeInt(bytes.length);
                out.write(bytes);
                out.writeDouble(logScores[Unigram.FIRST_PIECE_ID + i]);
            }
        }
    }

    /** Loads a model previously written by {@link #save(Path)}. */
    static UnigramModel loadInternal(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            int magic = in.readInt();
            if (magic != MODEL_MAGIC) {
                throw new IOException("Not a Tekitou Unigram model file");
            }
            int version = in.readInt();
            if (version != 1 && version != MODEL_VERSION) {
                throw new IOException("Unsupported Unigram model version: " + version);
            }
            // Version 1 used learned IDs starting at 256. The model payload is
            // otherwise compatible; loading it here remaps learned pieces to
            // start at 257 so ID 256 can be reserved for EOS.

            int pieceCount = in.readInt();
            if (pieceCount < 0 || pieceCount > MAX_MODEL_PIECES) {
                throw new IOException("Invalid piece count: " + pieceCount);
            }

            double[] scores = new double[Unigram.FIRST_PIECE_ID + pieceCount];
            scores[Unigram.EOS_ID] = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < Unigram.BYTE_VOCAB_SIZE; i++) {
                scores[i] = in.readDouble();
                if (!Double.isFinite(scores[i])) {
                    throw new IOException("Invalid byte-token score at ID " + i);
                }
            }

            String[] pieces = new String[pieceCount];
            byte[][] pieceBytes = new byte[pieceCount][];
            Map<String, Boolean> seen = new HashMap<>();

            for (int i = 0; i < pieceCount; i++) {
                int byteLength = in.readInt();
                if (byteLength < 2 || byteLength > MAX_PIECE_BYTES) {
                    throw new IOException("Invalid piece byte length: " + byteLength);
                }
                byte[] bytes = in.readNBytes(byteLength);
                if (bytes.length != byteLength) {
                    throw new EOFException("Truncated tokenizer model");
                }
                String piece = new String(bytes, StandardCharsets.UTF_8);
                if (!Arrays.equals(bytes, piece.getBytes(StandardCharsets.UTF_8))) {
                    throw new IOException("Invalid UTF-8 piece at index " + i);
                }
                if (seen.put(piece, Boolean.TRUE) != null) {
                    throw new IOException("Duplicate piece in model: " + piece);
                }
                pieces[i] = piece;
                pieceBytes[i] = bytes;
                scores[Unigram.FIRST_PIECE_ID + i] = in.readDouble();
                if (!Double.isFinite(scores[Unigram.FIRST_PIECE_ID + i])) {
                    throw new IOException("Invalid score for piece: " + piece);
                }
            }

            if (in.read() != -1) {
                throw new IOException("Unexpected trailing bytes in tokenizer model");
            }
            return new UnigramModel(pieces, pieceBytes, scores);
        }
    }

    private void checkTokenId(int tokenId) {
        if (tokenId < 0 || tokenId >= logScores.length) {
            throw new IllegalArgumentException("Token ID out of range: " + tokenId);
        }
    }

    
}
