package com.mc1510ty.TekitouAIStudio.tokenizer.bytelevelbpe;

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

/**
 * A simple byte-level BPE tokenizer.
 *
 * Token IDs:
 *   0..255 : raw UTF-8 bytes
 *   256    : EOS
 *   257..  : learned BPE tokens
 *
 * Training intentionally uses a straightforward full recount of all adjacent
 * pairs on every merge. This is easy to inspect, but is not intended for very
 * large corpora or 32k-vocabulary training without later optimization.
 */
public final class ByteLevelBPE {
    public static final int BYTE_VOCAB_SIZE = 256;
    public static final int EOS_ID = 256;
    public static final String EOS_TOKEN = "<|eos|>";
    public static final int FIRST_MERGED_ID = 257;

    private static final int MODEL_MAGIC = 0x54414250; // "TABP"
    private static final int MODEL_VERSION = 1;
    private static final int MAX_MERGES_ON_LOAD = 2_000_000;

    private final MergeRule[] merges;
    private final byte[][] tokenBytes;

    private ByteLevelBPE(List<MergeRule> rules) {
        this.merges = rules.toArray(MergeRule[]::new);
        this.tokenBytes = buildTokenBytes(this.merges);
    }

    /**
     * Trains until the requested total vocabulary size is reached, or no pair
     * occurs at least twice. The vocabulary size includes bytes and EOS.
     */
    public static ByteLevelBPE train(List<String> corpus, int vocabularySize) {
        return train(corpus, vocabularySize, 2L);
    }

    /**
     * @param minPairFrequency minimum frequency required before a pair is merged
     */
    public static ByteLevelBPE train(List<String> corpus,
                                     int vocabularySize,
                                     long minPairFrequency) {
        Objects.requireNonNull(corpus, "corpus");
        if (vocabularySize < FIRST_MERGED_ID) {
            throw new IllegalArgumentException(
                    "vocabularySize must be >= " + FIRST_MERGED_ID);
        }
        if (minPairFrequency < 1) {
            throw new IllegalArgumentException("minPairFrequency must be >= 1");
        }

        List<int[]> sequences = new ArrayList<>(corpus.size());
        for (String text : corpus) {
            Objects.requireNonNull(text, "corpus contains null");
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            int[] sequence = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                sequence[i] = bytes[i] & 0xFF;
            }
            if (sequence.length > 0) {
                sequences.add(sequence);
            }
        }

        if (sequences.isEmpty()) {
            throw new IllegalArgumentException("Training corpus is empty");
        }

        int targetMerges = vocabularySize - FIRST_MERGED_ID;
        List<MergeRule> rules = new ArrayList<>(targetMerges);

        while (rules.size() < targetMerges) {
            Map<Long, Long> pairCounts = countPairs(sequences);
            Pair best = findBestPair(pairCounts);
            if (best == null || best.count < minPairFrequency) {
                break;
            }

            int newTokenId = FIRST_MERGED_ID + rules.size();
            rules.add(new MergeRule(best.left, best.right));
            for (int i = 0; i < sequences.size(); i++) {
                sequences.set(i, mergeSequence(
                        sequences.get(i), best.left, best.right, newTokenId));
            }
        }

        return new ByteLevelBPE(rules);
    }

    private static Map<Long, Long> countPairs(List<int[]> sequences) {
        Map<Long, Long> counts = new HashMap<>();
        for (int[] sequence : sequences) {
            for (int i = 0; i + 1 < sequence.length; i++) {
                long key = pairKey(sequence[i], sequence[i + 1]);
                counts.merge(key, 1L, Long::sum);
            }
        }
        return counts;
    }

    private static Pair findBestPair(Map<Long, Long> counts) {
        long bestKey = -1L;
        long bestCount = 0L;
        for (Map.Entry<Long, Long> entry : counts.entrySet()) {
            long key = entry.getKey();
            long count = entry.getValue();
            // Stable tie-break: lowest (left ID, right ID) pair wins.
            if (count > bestCount || (count == bestCount && key < bestKey)) {
                bestKey = key;
                bestCount = count;
            }
        }
        if (bestKey < 0L) {
            return null;
        }
        return new Pair((int) (bestKey >>> 32), (int) bestKey, bestCount);
    }

    private static long pairKey(int left, int right) {
        return ((long) left << 32) | (right & 0xFFFF_FFFFL);
    }

    /** Replaces non-overlapping occurrences from left to right. */
    private static int[] mergeSequence(int[] source, int left, int right, int newTokenId) {
        int[] result = new int[source.length];
        int output = 0;
        int i = 0;
        while (i < source.length) {
            if (i + 1 < source.length && source[i] == left && source[i + 1] == right) {
                result[output++] = newTokenId;
                i += 2;
            } else {
                result[output++] = source[i++];
            }
        }
        return Arrays.copyOf(result, output);
    }

    /** Encodes text using the learned merge rules in training order. */
    public int[] encode(String text) {
        Objects.requireNonNull(text, "text");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int[] tokens = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            tokens[i] = bytes[i] & 0xFF;
        }

        for (int i = 0; i < merges.length; i++) {
            MergeRule rule = merges[i];
            tokens = mergeSequence(tokens, rule.left, rule.right, FIRST_MERGED_ID + i);
        }
        return tokens;
    }

    /** Encodes text and appends the dedicated EOS token. */
    public int[] encodeWithEos(String text) {
        return appendEos(encode(text));
    }

    /** Appends EOS without modifying the supplied array. */
    public int[] appendEos(int[] tokenIds) {
        Objects.requireNonNull(tokenIds, "tokenIds");
        for (int tokenId : tokenIds) {
            checkTokenId(tokenId);
        }
        if (tokenIds.length > 0 && tokenIds[tokenIds.length - 1] == EOS_ID) {
            return Arrays.copyOf(tokenIds, tokenIds.length);
        }
        int[] result = Arrays.copyOf(tokenIds, tokenIds.length + 1);
        result[tokenIds.length] = EOS_ID;
        return result;
    }

    /** Decodes token IDs to their original UTF-8 text. EOS is skipped. */
    public String decode(int[] tokenIds) {
        Objects.requireNonNull(tokenIds, "tokenIds");
        int estimatedSize = 0;
        for (int tokenId : tokenIds) {
            checkTokenId(tokenId);
            if (tokenId != EOS_ID) {
                estimatedSize = Math.addExact(estimatedSize, tokenBytes[tokenId].length);
            }
        }

        byte[] output = new byte[estimatedSize];
        int offset = 0;
        for (int tokenId : tokenIds) {
            if (tokenId == EOS_ID) {
                continue;
            }
            byte[] bytes = tokenBytes[tokenId];
            System.arraycopy(bytes, 0, output, offset, bytes.length);
            offset += bytes.length;
        }
        return new String(output, StandardCharsets.UTF_8);
    }

    public int vocabularySize() {
        return tokenBytes.length;
    }

    public int mergeCount() {
        return merges.length;
    }

    /**
     * Returns a defensive copy of a token's raw bytes. A learned token may end
     * in the middle of a UTF-8 character, so callers should not assume it is a
     * valid standalone String.
     */
    public byte[] getTokenBytes(int tokenId) {
        checkTokenId(tokenId);
        return Arrays.copyOf(tokenBytes[tokenId], tokenBytes[tokenId].length);
    }

    /** Human-readable token label, using hexadecimal for invalid UTF-8 fragments. */
    public String getTokenDescription(int tokenId) {
        checkTokenId(tokenId);
        if (tokenId < BYTE_VOCAB_SIZE) {
            return String.format("<0x%02X>", tokenId);
        }
        if (tokenId == EOS_ID) {
            return EOS_TOKEN;
        }
        byte[] bytes = tokenBytes[tokenId];
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        if (Arrays.equals(bytes, decoded.getBytes(StandardCharsets.UTF_8))) {
            return decoded;
        }
        StringBuilder hex = new StringBuilder("<");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                hex.append(' ');
            }
            hex.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return hex.append('>').toString();
    }

    /** Saves the ordered merge rules. Token byte arrays are reconstructed on load. */
    public void save(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(MODEL_MAGIC);
            out.writeInt(MODEL_VERSION);
            out.writeInt(merges.length);
            for (MergeRule rule : merges) {
                out.writeInt(rule.left);
                out.writeInt(rule.right);
            }
        }
    }

    public static ByteLevelBPE load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != MODEL_MAGIC) {
                throw new IOException("Not a Tekitou ByteLevelBPE model file");
            }
            int version = in.readInt();
            if (version != MODEL_VERSION) {
                throw new IOException("Unsupported ByteLevelBPE model version: " + version);
            }
            int mergeCount = in.readInt();
            if (mergeCount < 0 || mergeCount > MAX_MERGES_ON_LOAD) {
                throw new IOException("Invalid merge count: " + mergeCount);
            }

            List<MergeRule> rules = new ArrayList<>(mergeCount);
            int nextTokenId = FIRST_MERGED_ID;
            for (int i = 0; i < mergeCount; i++) {
                int left = in.readInt();
                int right = in.readInt();
                if (left < 0 || left >= nextTokenId || left == EOS_ID
                        || right < 0 || right >= nextTokenId || right == EOS_ID) {
                    throw new IOException("Invalid merge rule at index " + i);
                }
                rules.add(new MergeRule(left, right));
                nextTokenId++;
            }
            if (in.read() != -1) {
                throw new IOException("Unexpected trailing bytes in model file");
            }
            return new ByteLevelBPE(rules);
        } catch (EOFException e) {
            throw new IOException("Truncated ByteLevelBPE model file", e);
        }
    }

    private static byte[][] buildTokenBytes(MergeRule[] rules) {
        byte[][] result = new byte[FIRST_MERGED_ID + rules.length][];
        for (int i = 0; i < BYTE_VOCAB_SIZE; i++) {
            result[i] = new byte[]{(byte) i};
        }
        result[EOS_ID] = new byte[0];

        for (int i = 0; i < rules.length; i++) {
            MergeRule rule = rules[i];
            int newTokenId = FIRST_MERGED_ID + i;
            if (rule.left < 0 || rule.left >= newTokenId || rule.left == EOS_ID
                    || rule.right < 0 || rule.right >= newTokenId || rule.right == EOS_ID) {
                throw new IllegalArgumentException("Invalid merge rule at index " + i);
            }
            byte[] left = result[rule.left];
            byte[] right = result[rule.right];
            byte[] merged = Arrays.copyOf(left, left.length + right.length);
            System.arraycopy(right, 0, merged, left.length, right.length);
            result[newTokenId] = merged;
        }
        return result;
    }

    private void checkTokenId(int tokenId) {
        if (tokenId < 0 || tokenId >= tokenBytes.length) {
            throw new IllegalArgumentException("Token ID out of range: " + tokenId);
        }
    }

    private static final class MergeRule {
        final int left;
        final int right;

        MergeRule(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    private static final class Pair {
        final int left;
        final int right;
        final long count;

        Pair(int left, int right, long count) {
            this.left = left;
            this.right = right;
            this.count = count;
        }
    }
}
