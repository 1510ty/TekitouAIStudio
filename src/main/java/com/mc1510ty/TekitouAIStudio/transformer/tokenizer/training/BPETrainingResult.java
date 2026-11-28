package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.BpeVocabulary;

import java.util.Objects;

public record BPETrainingResult(
        BpeVocabulary vocabulary,
        int initialVocabSize,
        int targetVocabSize,
        long initialTokenCount,
        long finalTokenCount,
        StopReason stopReason
) {

    public BPETrainingResult {
        Objects.requireNonNull(
                vocabulary,
                "vocabulary must not be null"
        );

        Objects.requireNonNull(
                stopReason,
                "stopReason must not be null"
        );

        if (initialVocabSize < 274) {
            throw new IllegalArgumentException(
                    "initialVocabSize must be at least 274"
            );
        }

        if (targetVocabSize < initialVocabSize) {
            throw new IllegalArgumentException(
                    "targetVocabSize must not be smaller than initialVocabSize"
            );
        }

        int finalVocabSize = vocabulary.vocabSize();

        if (finalVocabSize < initialVocabSize) {
            throw new IllegalArgumentException(
                    "Final vocabulary size must not be smaller than initialVocabSize"
            );
        }

        if (finalVocabSize > targetVocabSize) {
            throw new IllegalArgumentException(
                    "Final vocabulary size exceeds targetVocabSize"
            );
        }

        if (initialTokenCount < 0 || finalTokenCount < 0) {
            throw new IllegalArgumentException(
                    "Token counts must not be negative"
            );
        }

        if (finalTokenCount > initialTokenCount) {
            throw new IllegalArgumentException(
                    "finalTokenCount must not exceed initialTokenCount"
            );
        }

        if (stopReason == StopReason.TARGET_VOCAB_SIZE_REACHED
                && finalVocabSize != targetVocabSize) {

            throw new IllegalArgumentException(
                    "Target vocabulary size was not reached"
            );
        }

        if (stopReason == StopReason.MIN_PAIR_FREQUENCY_NOT_MET
                && finalVocabSize >= targetVocabSize) {

            throw new IllegalArgumentException(
                    "Minimum pair frequency cannot be the stop reason when target size was reached"
            );
        }
    }

    /**
     * 学習終了時の語彙数。
     */
    public int finalVocabSize() {
        return vocabulary.vocabSize();
    }

    /**
     * 今回追加されたマージルール数。
     */
    public int completedMergeCount() {
        return finalVocabSize() - initialVocabSize;
    }

    /**
     * 目標語彙数まで残っているマージ数。
     */
    public int remainingMergeCount() {
        return targetVocabSize - finalVocabSize();
    }

    /**
     * 目標語彙数に到達したか。
     */
    public boolean targetVocabSizeReached() {
        return stopReason == StopReason.TARGET_VOCAB_SIZE_REACHED;
    }

    public enum StopReason {

        /**
         * 目標語彙数に到達した。
         */
        TARGET_VOCAB_SIZE_REACHED,

        /**
         * 最小ペア出現頻度を満たすペアがなくなった。
         */
        MIN_PAIR_FREQUENCY_NOT_MET
    }
}