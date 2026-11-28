
package com.mc1510ty.TekitouAIStudio.transformer.tokenizer;

import java.util.Comparator;
import java.util.PriorityQueue;

final class BpeEncoder {

    private BpeEncoder() {
    }

    private record MergeCandidate(
            int rank,
            int left,
            int right,
            long leftVersion,
            long rightVersion
    ) {
    }

    /**
     * UTF-8バイト列をBPEでMergeする。
     */
    static int[] encode(
            byte[] bytes,
            BpeVocabulary vocabulary
    ) {

        int length = bytes.length;

        if (length == 0) {
            return new int[0];
        }

        int[] tokenIds = new int[length];

        int[] previous = new int[length];
        int[] next = new int[length];

        long[] versions = new long[length];

        boolean[] active = new boolean[length];

        // 初期Token IDを設定
        for (int i = 0; i < length; i++) {

            tokenIds[i] = bytes[i] & 0xFF;

            previous[i] = i - 1;
            next[i] = i + 1 < length ? i + 1 : -1;

            active[i] = true;
        }

        // Merge Rank優先のキュー
        PriorityQueue<MergeCandidate> candidates =
                new PriorityQueue<>(
                        Comparator
                                .comparingInt(MergeCandidate::rank)
                                .thenComparingInt(MergeCandidate::left)
                );

        // 初期Merge候補を登録
        for (int i = 0; i < length - 1; i++) {
            addCandidate(
                    i, i + 1,
                    tokenIds, next, versions, active,
                    vocabulary, candidates
            );
        }

        // Merge実行
        while (!candidates.isEmpty()) {

            MergeCandidate candidate = candidates.poll();

            int left = candidate.left();
            int right = candidate.right();

            // 古くなった候補を除外
            if (!active[left] ||
                    !active[right] ||
                    next[left] != right ||
                    versions[left] != candidate.leftVersion() ||
                    versions[right] != candidate.rightVersion()) {

                continue;
            }

            int preceding = previous[left];
            int following = next[right];

            // Merge Rankから新しいToken IDを取得
            tokenIds[left] =
                    SpecialTokens.FIRST_BPE_TOKEN_ID
                            + candidate.rank();

            versions[left]++;

            // 右ノードを削除
            active[right] = false;
            versions[right]++;

            // リンクを更新
            next[left] = following;

            if (following != -1) {
                previous[following] = left;
            }

            previous[right] = -1;
            next[right] = -1;

            // 新しく隣接したペアを登録
            addCandidate(
                    preceding, left,
                    tokenIds, next, versions, active,
                    vocabulary, candidates
            );

            addCandidate(
                    left, following,
                    tokenIds, next, versions, active,
                    vocabulary, candidates
            );
        }

        // 最終結果を生成
        int count = 0;

        for (int node = 0; node != -1; node = next[node]) {
            count++;
        }

        int[] result = new int[count];

        int index = 0;

        for (int node = 0; node != -1; node = next[node]) {
            result[index++] = tokenIds[node];
        }

        return result;
    }

    /**
     * Merge候補を追加する。
     */
    private static void addCandidate(
            int left,
            int right,
            int[] tokenIds,
            int[] next,
            long[] versions,
            boolean[] active,
            BpeVocabulary vocabulary,
            PriorityQueue<MergeCandidate> candidates
    ) {

        if (left < 0 || right < 0) {
            return;
        }

        if (!active[left] ||
                !active[right] ||
                next[left] != right) {

            return;
        }

        int rank = vocabulary.findMergeRank(
                tokenIds[left],
                tokenIds[right]
        );

        if (rank < 0) {
            return;
        }

        candidates.add(
                new MergeCandidate(
                        rank,
                        left,
                        right,
                        versions[left],
                        versions[right]
                )
        );
    }
}
