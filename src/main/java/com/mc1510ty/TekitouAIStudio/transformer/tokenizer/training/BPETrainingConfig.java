package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.SpecialTokens;

public record BPETrainingConfig(
        int targetVocabSize,
        long minPairFrequency,
        int workerCount
) {

    public static final int DEFAULT_TARGET_VOCAB_SIZE = 100_000;

    public static final long DEFAULT_MIN_PAIR_FREQUENCY = 2;

    /**
     * 設定値の検証。
     */
    public BPETrainingConfig {

        if (targetVocabSize < SpecialTokens.FIRST_BPE_TOKEN_ID) {
            throw new IllegalArgumentException(
                    "目標語彙数は274以上にしてください"
            );
        }

        if (minPairFrequency < 1) {
            throw new IllegalArgumentException(
                    "最低出現頻度は1以上にしてください"
            );
        }

        if (workerCount < 1) {
            throw new IllegalArgumentException(
                    "Worker数は1以上にしてください"
            );
        }
    }

    /**
     * デフォルト設定を生成する。
     */
    public static BPETrainingConfig defaults() {

        return new BPETrainingConfig(
                DEFAULT_TARGET_VOCAB_SIZE,
                DEFAULT_MIN_PAIR_FREQUENCY,
                Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * 初回Tokenizer作成時に必要なMerge回数。
     */
    public int getInitialMergeCount() {

        return targetVocabSize
                - SpecialTokens.FIRST_BPE_TOKEN_ID;
    }

    /**
     * 現在の語彙数から目標語彙数までの追加Merge回数。
     */
    public int getAdditionalMergeCount(int currentVocabSize) {

        if (currentVocabSize < SpecialTokens.FIRST_BPE_TOKEN_ID) {
            throw new IllegalArgumentException(
                    "現在の語彙数が不正です"
            );
        }

        if (currentVocabSize > targetVocabSize) {
            throw new IllegalArgumentException(
                    "現在の語彙数が目標語彙数を超えています"
            );
        }

        return targetVocabSize - currentVocabSize;
    }
}
