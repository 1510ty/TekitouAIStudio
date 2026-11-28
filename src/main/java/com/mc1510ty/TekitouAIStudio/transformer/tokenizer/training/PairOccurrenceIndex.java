package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.SpecialTokens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PairOccurrenceIndex {

    private static final long NO_PAIR = -1L;

    // トークンノード
    private final int[] tokenIds;
    private final int[] previous;
    private final int[] next;

    // ペア出現リスト内のリンク
    private final int[] occurrencePrevious;
    private final int[] occurrenceNext;
    private final long[] occurrencePair;

    // 各Sequenceの先頭ノード
    private final int[] sequenceHeads;

    // ペアID -> 出現位置一覧
    private final Map<Long, PairBucket> pairBuckets =
            new HashMap<>();

    // 前回の集計以降に発生した頻度の差分
    private final Map<Long, Long> pendingFrequencyDeltas =
            new HashMap<>();

    private long tokenCount;
    private int maxTokenId = -1;

    /**
     * 指定されたトークン列からインデックスを構築する。
     *
     * 配列ごとに独立したSequenceとして扱う。
     */
    public PairOccurrenceIndex(List<int[]> sequences) {
        Objects.requireNonNull(sequences, "sequences must not be null");

        List<int[]> snapshot = new ArrayList<>(sequences.size());

        long totalTokens = 0;

        for (int i = 0; i < sequences.size(); i++) {
            int[] sequence = Objects.requireNonNull(
                    sequences.get(i),
                    "sequences[" + i + "] must not be null"
            );

            snapshot.add(sequence);

            totalTokens += sequence.length;

            if (totalTokens > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Total token count exceeds the supported limit"
                );
            }
        }

        int nodeCount = (int) totalTokens;

        tokenIds = new int[nodeCount];
        previous = new int[nodeCount];
        next = new int[nodeCount];

        occurrencePrevious = new int[nodeCount];
        occurrenceNext = new int[nodeCount];
        occurrencePair = new long[nodeCount];

        sequenceHeads = new int[snapshot.size()];

        Arrays.fill(previous, -1);
        Arrays.fill(next, -1);
        Arrays.fill(occurrencePrevious, -1);
        Arrays.fill(occurrenceNext, -1);
        Arrays.fill(occurrencePair, NO_PAIR);
        Arrays.fill(sequenceHeads, -1);

        tokenCount = nodeCount;

        // ノードを生成してSequenceごとにリンクする
        int offset = 0;

        for (int sequenceIndex = 0;
             sequenceIndex < snapshot.size();
             sequenceIndex++) {

            int[] sequence = snapshot.get(sequenceIndex);

            if (sequence.length == 0) {
                continue;
            }

            sequenceHeads[sequenceIndex] = offset;

            for (int i = 0; i < sequence.length; i++) {
                int tokenId = sequence[i];

                if (tokenId < 0) {
                    throw new IllegalArgumentException(
                            "Token IDs must not be negative"
                    );
                }

                tokenIds[offset + i] = tokenId;

                maxTokenId = Math.max(maxTokenId, tokenId);

                if (i > 0) {
                    previous[offset + i] = offset + i - 1;
                }

                if (i < sequence.length - 1) {
                    next[offset + i] = offset + i + 1;
                }
            }

            offset += sequence.length;
        }

        // 初期ペア頻度を構築する
        for (int node = 0; node < nodeCount; node++) {
            addOccurrence(node, false);
        }
    }

    /**
     * 現在のペア頻度を取得する。
     */
    public Map<Long, Long> getPairFrequencies() {
        Map<Long, Long> result = new HashMap<>();

        for (Map.Entry<Long, PairBucket> entry :
                pairBuckets.entrySet()) {

            result.put(entry.getKey(), entry.getValue().count);
        }

        return Map.copyOf(result);
    }

    /**
     * 特定のペアの現在の出現回数を取得する。
     */
    public long getPairFrequency(long pair) {
        PairBucket bucket = pairBuckets.get(pair);

        return bucket == null ? 0 : bucket.count;
    }

    /**
     * 前回取得してから発生した頻度の差分を取得する。
     *
     * 取得後、内部の差分はクリアされる。
     */
    public Map<Long, Long> drainFrequencyDeltas() {
        if (pendingFrequencyDeltas.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> result =
                Map.copyOf(pendingFrequencyDeltas);

        pendingFrequencyDeltas.clear();

        return result;
    }

    /**
     * 指定ペアを新しいトークンIDにマージする。
     *
     * このインデックス内に該当ペアが存在しない場合は0を返す。
     * 別ワーカーにだけ存在するペアを処理するときにも使用する。
     *
     * @return 実際にマージした出現回数
     */
    public long mergePair(long pair, int newTokenId) {
        validateMerge(pair, newTokenId);

        // 次のマージIDも単調増加するよう管理する
        maxTokenId = newTokenId;

        long mergedCount = 0;

        while (true) {
            PairBucket bucket = pairBuckets.get(pair);

            if (bucket == null) {
                break;
            }

            int leftNode = bucket.headNode;
            int rightNode = next[leftNode];

            if (rightNode < 0
                    || tokenIds[leftNode] != PairFrequencyCounter.leftTokenId(pair)
                    || tokenIds[rightNode] != PairFrequencyCounter.rightTokenId(pair)) {

                throw new IllegalStateException(
                        "Pair occurrence index is inconsistent"
                );
            }

            mergeNodes(leftNode, rightNode, newTokenId);

            mergedCount++;
        }

        return mergedCount;
    }

    /**
     * トークン列を現在の状態で再構築する。
     */
    public List<int[]> getSequences() {
        List<int[]> result = new ArrayList<>(sequenceHeads.length);

        for (int head : sequenceHeads) {
            int length = 0;

            for (int node = head;
                 node != -1;
                 node = next[node]) {

                length++;
            }

            int[] sequence = new int[length];

            int index = 0;

            for (int node = head;
                 node != -1;
                 node = next[node]) {

                sequence[index++] = tokenIds[node];
            }

            result.add(sequence);
        }

        return List.copyOf(result);
    }

    public long getTokenCount() {
        return tokenCount;
    }

    public int getSequenceCount() {
        return sequenceHeads.length;
    }

    /**
     * 指定された2ノードをマージする。
     */
    private void mergeNodes(
            int leftNode,
            int rightNode,
            int mergedTokenId
    ) {
        int previousNode = previous[leftNode];
        int nextNode = next[rightNode];

        // 変更される可能性のある既存ペアを削除
        removeOccurrence(leftNode);
        removeOccurrence(previousNode);
        removeOccurrence(rightNode);

        // 左側のノードを新しいトークンに変更
        tokenIds[leftNode] = mergedTokenId;

        // 右側のノードをリンクから除外
        next[leftNode] = nextNode;

        if (nextNode != -1) {
            previous[nextNode] = leftNode;
        }

        tokenIds[rightNode] = -1;
        previous[rightNode] = -1;
        next[rightNode] = -1;

        // トークン数を更新
        tokenCount--;

        // 新しくできた周辺のペアを追加
        addOccurrence(previousNode, true);
        addOccurrence(leftNode, true);
    }

    /**
     * 指定ノードを左端とするペアを登録する。
     */
    private void addOccurrence(int leftNode, boolean trackDelta) {
        if (leftNode < 0) {
            return;
        }

        if (occurrencePair[leftNode] != NO_PAIR) {
            throw new IllegalStateException(
                    "Pair occurrence is already registered"
            );
        }

        int rightNode = next[leftNode];

        if (rightNode == -1) {
            return;
        }

        int leftTokenId = tokenIds[leftNode];
        int rightTokenId = tokenIds[rightNode];

        if (SpecialTokens.isSpecialToken(leftTokenId)
                || SpecialTokens.isSpecialToken(rightTokenId)) {
            return;
        }

        long pair = PairFrequencyCounter.packPair(
                leftTokenId,
                rightTokenId
        );

        PairBucket bucket = pairBuckets.computeIfAbsent(
                pair,
                ignored -> new PairBucket()
        );

        int head = bucket.headNode;
        int tail = bucket.tailNode;

        if (head == -1) {
            // 最初の出現位置
            bucket.headNode = leftNode;
            bucket.tailNode = leftNode;

            occurrencePrevious[leftNode] = -1;
            occurrenceNext[leftNode] = -1;

        } else if (leftNode < head) {
            // 先頭に追加
            occurrencePrevious[leftNode] = -1;
            occurrenceNext[leftNode] = head;

            occurrencePrevious[head] = leftNode;
            bucket.headNode = leftNode;

        } else if (leftNode > tail) {
            // 末尾に追加
            occurrencePrevious[leftNode] = tail;
            occurrenceNext[leftNode] = -1;

            occurrenceNext[tail] = leftNode;
            bucket.tailNode = leftNode;

        } else {
            // 中間に挿入
            int current = head;

            while (current != -1 && current < leftNode) {
                current = occurrenceNext[current];
            }

            int previousOccurrence = occurrencePrevious[current];

            occurrencePrevious[leftNode] = previousOccurrence;
            occurrenceNext[leftNode] = current;

            occurrenceNext[previousOccurrence] = leftNode;
            occurrencePrevious[current] = leftNode;
        }

        bucket.count++;

        occurrencePair[leftNode] = pair;

        if (trackDelta) {
            recordFrequencyDelta(pair, 1);
        }
    }

    /**
     * 指定ノードを左端とするペアを削除する。
     */
    private void removeOccurrence(int leftNode) {
        if (leftNode < 0) {
            return;
        }

        long pair = occurrencePair[leftNode];

        if (pair == NO_PAIR) {
            return;
        }

        PairBucket bucket = pairBuckets.get(pair);

        if (bucket == null) {
            throw new IllegalStateException(
                    "Pair bucket does not exist"
            );
        }

        int previousOccurrence =
                occurrencePrevious[leftNode];

        int nextOccurrence =
                occurrenceNext[leftNode];

        // 前後の出現位置を接続し直す
        if (previousOccurrence == -1) {
            bucket.headNode = nextOccurrence;
        } else {
            occurrenceNext[previousOccurrence] = nextOccurrence;
        }

        if (nextOccurrence == -1) {
            bucket.tailNode = previousOccurrence;
        } else {
            occurrencePrevious[nextOccurrence] = previousOccurrence;
        }

        bucket.count--;

        occurrencePair[leftNode] = NO_PAIR;
        occurrencePrevious[leftNode] = -1;
        occurrenceNext[leftNode] = -1;

        recordFrequencyDelta(pair, -1);

        if (bucket.count == 0) {
            pairBuckets.remove(pair);
        }
    }

    /**
     * 頻度変更を差分として記録する。
     */
    private void recordFrequencyDelta(long pair, long delta) {
        pendingFrequencyDeltas.compute(
                pair,
                (ignored, current) -> {
                    long updated = (current == null ? 0 : current) + delta;

                    return updated == 0 ? null : updated;
                }
        );
    }

    private void validateMerge(long pair, int newTokenId) {
        if (pair < 0) {
            throw new IllegalArgumentException(
                    "Invalid pair ID"
            );
        }

        int left = PairFrequencyCounter.leftTokenId(pair);
        int right = PairFrequencyCounter.rightTokenId(pair);

        if (left < 0 || right < 0) {
            throw new IllegalArgumentException(
                    "Token IDs must not be negative"
            );
        }

        if (SpecialTokens.isSpecialToken(left)
                || SpecialTokens.isSpecialToken(right)) {
            throw new IllegalArgumentException(
                    "Special tokens cannot be merged"
            );
        }

        if (newTokenId < SpecialTokens.FIRST_BPE_TOKEN_ID
                || SpecialTokens.isSpecialToken(newTokenId)) {
            throw new IllegalArgumentException(
                    "Invalid new token ID"
            );
        }

        if (newTokenId <= maxTokenId) {
            throw new IllegalArgumentException(
                    "New token ID must be greater than existing token IDs"
            );
        }
    }

    /**
     * 1種類のペアの出現位置一覧。
     */
    private static final class PairBucket {

        private int headNode = -1;
        private int tailNode = -1;
        private long count;
    }
}