package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BPETrainingWorker {

    private final int workerId;
    private final PairOccurrenceIndex occurrenceIndex;

    public BPETrainingWorker(
            int workerId,
            List<int[]> sequences
    ) {
        if (workerId < 0) {
            throw new IllegalArgumentException(
                    "workerId must not be negative"
            );
        }

        this.workerId = workerId;

        this.occurrenceIndex = new PairOccurrenceIndex(
                Objects.requireNonNull(
                        sequences,
                        "sequences must not be null"
                )
        );
    }

    /**
     * ワーカーIDを取得する。
     */
    public int getWorkerId() {
        return workerId;
    }

    /**
     * 初期ペア頻度を取得する。
     *
     * 初期化時、マージを開始する前に呼び出す。
     */
    public Map<Long, Long> getInitialPairFrequencies() {
        return occurrenceIndex.getPairFrequencies();
    }

    /**
     * 指定ペアを担当データ内でマージする。
     *
     * 該当ペアが存在しない場合、mergedCountは0になる。
     */
    public MergeResult mergePair(
            long pair,
            int newTokenId
    ) {
        long mergedCount = occurrenceIndex.mergePair(
                pair,
                newTokenId
        );

        Map<Long, Long> frequencyDeltas =
                occurrenceIndex.drainFrequencyDeltas();

        return new MergeResult(
                mergedCount,
                frequencyDeltas
        );
    }

    /**
     * 現在のトークン数を取得する。
     */
    public long getTokenCount() {
        return occurrenceIndex.getTokenCount();
    }

    /**
     * ワーカーのマージ結果。
     *
     * mergedCount:
     * 実際にマージされたペアの数
     *
     * frequencyDeltas:
     * ペア頻度の増減
     */
    public record MergeResult(
            long mergedCount,
            Map<Long, Long> frequencyDeltas
    ) {
        public MergeResult {
            if (mergedCount < 0) {
                throw new IllegalArgumentException(
                        "mergedCount must not be negative"
                );
            }

            frequencyDeltas = Map.copyOf(
                    Objects.requireNonNull(
                            frequencyDeltas,
                            "frequencyDeltas must not be null"
                    )
            );
        }
    }
}