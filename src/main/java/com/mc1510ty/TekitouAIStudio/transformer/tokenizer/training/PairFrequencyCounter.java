package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.SpecialTokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class PairFrequencyCounter {

    private PairFrequencyCounter() {
    }

    /**
     * トークン列全体から隣接ペアの出現回数を集計する。
     *
     * @param sequences  集計対象のトークン列
     * @param workerCount 並列処理数
     * @return パックされたトークンペアと出現回数のMap
     */
    public static Map<Long, Long> count(
            List<int[]> sequences,
            int workerCount
    ) {
        Objects.requireNonNull(sequences, "sequences must not be null");

        if (workerCount < 1) {
            throw new IllegalArgumentException(
                    "workerCount must be at least 1"
            );
        }

        // 配列への参照をコピーし、リスト構造を固定する
        List<int[]> snapshot = new ArrayList<>(sequences.size());

        long totalPairCount = 0;

        for (int i = 0; i < sequences.size(); i++) {
            int[] tokens = Objects.requireNonNull(
                    sequences.get(i),
                    "sequences[" + i + "] must not be null"
            );

            snapshot.add(tokens);
            totalPairCount += Math.max(0, tokens.length - 1);
        }

        Map<Long, Long> result = new HashMap<>();

        if (totalPairCount == 0) {
            return result;
        }

        // トークン列をペア単位で分割する
        List<List<SequenceRange>> partitions =
                partition(snapshot, totalPairCount, workerCount);

        // ワーカーが1つならスレッドを生成しない
        if (partitions.size() == 1) {
            return countRanges(partitions.getFirst());
        }

        ExecutorService executor =
                Executors.newFixedThreadPool(partitions.size());

        try {
            List<Callable<Map<Long, Long>>> tasks = new ArrayList<>();

            for (List<SequenceRange> partition : partitions) {
                tasks.add(() -> countRanges(partition));
            }

            List<Future<Map<Long, Long>>> futures =
                    executor.invokeAll(tasks);

            // 各ワーカーの集計結果を統合する
            for (Future<Map<Long, Long>> future : futures) {
                Map<Long, Long> localResult = future.get();

                for (Map.Entry<Long, Long> entry :
                        localResult.entrySet()) {

                    result.merge(
                            entry.getKey(),
                            entry.getValue(),
                            Long::sum
                    );
                }
            }

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Pair frequency counting was interrupted",
                    e
            );

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            throw new IllegalStateException(
                    "Pair frequency counting failed",
                    cause
            );

        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 全トークン列をワーカー数に応じて分割する。
     * 分割位置はペア単位なので、配列境界をまたぐペアは作られない。
     */
    private static List<List<SequenceRange>> partition(
            List<int[]> sequences,
            long totalPairCount,
            int requestedWorkers
    ) {
        int workerCount = (int) Math.min(
                (long) requestedWorkers,
                totalPairCount
        );

        List<List<SequenceRange>> partitions =
                new ArrayList<>(workerCount);

        for (int i = 0; i < workerCount; i++) {
            partitions.add(new ArrayList<>());
        }

        long baseCount = totalPairCount / workerCount;
        long remainder = totalPairCount % workerCount;

        int sequenceIndex = 0;
        int pairIndex = 0;

        for (int worker = 0; worker < workerCount; worker++) {
            long remaining = baseCount
                    + (worker < remainder ? 1 : 0);

            while (remaining > 0) {

                // ペアの残っているトークン列まで進める
                while (sequenceIndex < sequences.size()
                        && pairIndex >= sequences.get(sequenceIndex).length - 1) {

                    sequenceIndex++;
                    pairIndex = 0;
                }

                int[] tokens = sequences.get(sequenceIndex);

                int available = tokens.length - 1 - pairIndex;

                int count = (int) Math.min(
                        remaining,
                        available
                );

                partitions.get(worker).add(
                        new SequenceRange(
                                tokens,
                                pairIndex,
                                pairIndex + count
                        )
                );

                pairIndex += count;
                remaining -= count;
            }
        }

        return partitions;
    }

    /**
     * 割り当てられた範囲のペアを集計する。
     */
    private static Map<Long, Long> countRanges(
            List<SequenceRange> ranges
    ) {
        Map<Long, Long> frequencies = new HashMap<>();

        for (SequenceRange range : ranges) {
            int[] tokens = range.tokens();

            for (int i = range.startPairIndex();
                 i < range.endPairIndex();
                 i++) {

                int left = tokens[i];
                int right = tokens[i + 1];

                if (left < 0 || right < 0) {
                    throw new IllegalArgumentException(
                            "Token IDs must not be negative"
                    );
                }

                // 特殊トークンを含むペアは集計しない
                if (SpecialTokens.isSpecialToken(left)
                        || SpecialTokens.isSpecialToken(right)) {
                    continue;
                }

                long pair = packPair(left, right);

                frequencies.merge(pair, 1L, Long::sum);
            }
        }

        return frequencies;
    }

    /**
     * 2つのToken IDを1つのlongに格納する。
     */
    public static long packPair(int leftTokenId, int rightTokenId) {
        if (leftTokenId < 0 || rightTokenId < 0) {
            throw new IllegalArgumentException(
                    "Token IDs must not be negative"
            );
        }

        return ((long) leftTokenId << 32)
                | (rightTokenId & 0xFFFF_FFFFL);
    }

    public static int leftTokenId(long pair) {
        return (int) (pair >>> 32);
    }

    public static int rightTokenId(long pair) {
        return (int) pair;
    }

    /**
     * 1ワーカーに割り当てるペア範囲。
     */
    private record SequenceRange(
            int[] tokens,
            int startPairIndex,
            int endPairIndex
    ) {
    }
}