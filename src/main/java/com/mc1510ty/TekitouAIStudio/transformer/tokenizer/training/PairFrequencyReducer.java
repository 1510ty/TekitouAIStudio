package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.SpecialTokens;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PairFrequencyReducer {

    private final Map<Long, Long> pairFrequencies =
            new HashMap<>();

    private boolean initialized;

    /**
     * 全ワーカーの初期ペア頻度を集約する。
     *
     * マージ開始前に一度だけ呼び出す。
     */
    public void initialize(
            Collection<BPETrainingWorker> workers
    ) {
        Objects.requireNonNull(
                workers,
                "workers must not be null"
        );

        if (initialized) {
            throw new IllegalStateException(
                    "Reducer is already initialized"
            );
        }

        Map<Long, Long> aggregated = new HashMap<>();
        Set<Integer> workerIds = new HashSet<>();

        for (BPETrainingWorker worker : workers) {
            Objects.requireNonNull(
                    worker,
                    "Worker must not be null"
            );

            if (!workerIds.add(worker.getWorkerId())) {
                throw new IllegalArgumentException(
                        "Duplicate worker ID: " + worker.getWorkerId()
                );
            }

            Map<Long, Long> frequencies =
                    worker.getInitialPairFrequencies();

            for (Map.Entry<Long, Long> entry :
                    frequencies.entrySet()) {

                long pair = entry.getKey();
                long frequency = entry.getValue();

                validatePair(pair);

                if (frequency < 1) {
                    throw new IllegalArgumentException(
                            "Pair frequency must be positive"
                    );
                }

                aggregated.merge(
                        pair,
                        frequency,
                        Math::addExact
                );
            }
        }

        pairFrequencies.putAll(aggregated);
        initialized = true;
    }

    /**
     * ワーカーのマージ結果を反映する。
     */
    public void applyWorkerResult(
            BPETrainingWorker.MergeResult result
    ) {
        Objects.requireNonNull(
                result,
                "result must not be null"
        );

        applyDeltas(result.frequencyDeltas());
    }

    /**
     * ペア頻度の差分を一括適用する。
     *
     * 負の頻度になる場合は適用しない。
     * 全差分を検証してから反映するため、
     * 不正な差分による部分更新は発生しない。
     */
    public void applyDeltas(
            Map<Long, Long> frequencyDeltas
    ) {
        ensureInitialized();

        Objects.requireNonNull(
                frequencyDeltas,
                "frequencyDeltas must not be null"
        );

        Map<Long, Long> updatedFrequencies =
                new HashMap<>();

        // すべての差分を先に検証する
        for (Map.Entry<Long, Long> entry :
                frequencyDeltas.entrySet()) {

            long pair = Objects.requireNonNull(
                    entry.getKey(),
                    "Pair ID must not be null"
            );

            long delta = Objects.requireNonNull(
                    entry.getValue(),
                    "Frequency delta must not be null"
            );

            validatePair(pair);

            if (delta == 0) {
                continue;
            }

            long currentFrequency =
                    pairFrequencies.getOrDefault(pair, 0L);

            long updatedFrequency =
                    Math.addExact(currentFrequency, delta);

            if (updatedFrequency < 0) {
                throw new IllegalArgumentException(
                        "Pair frequency cannot become negative"
                );
            }

            updatedFrequencies.put(
                    pair,
                    updatedFrequency
            );
        }

        // 検証完了後に反映する
        for (Map.Entry<Long, Long> entry :
                updatedFrequencies.entrySet()) {

            long pair = entry.getKey();
            long frequency = entry.getValue();

            if (frequency == 0) {
                pairFrequencies.remove(pair);
            } else {
                pairFrequencies.put(pair, frequency);
            }
        }
    }

    /**
     * 最頻出ペアを取得する。
     *
     * 同率の場合はペアIDの昇順で選択する。
     * 指定頻度未満のペアは選択対象外。
     */
    public Optional<MostFrequentPair> getMostFrequentPair(
            long minPairFrequency
    ) {
        ensureInitialized();

        if (minPairFrequency < 1) {
            throw new IllegalArgumentException(
                    "minPairFrequency must be at least 1"
            );
        }

        long bestPair = -1;
        long bestFrequency = 0;

        for (Map.Entry<Long, Long> entry :
                pairFrequencies.entrySet()) {

            long pair = entry.getKey();
            long frequency = entry.getValue();

            if (frequency < minPairFrequency) {
                continue;
            }

            if (frequency > bestFrequency
                    || (frequency == bestFrequency
                    && (bestPair == -1 || pair < bestPair))) {

                bestPair = pair;
                bestFrequency = frequency;
            }
        }

        if (bestPair == -1) {
            return Optional.empty();
        }

        return Optional.of(
                new MostFrequentPair(
                        bestPair,
                        bestFrequency
                )
        );
    }

    /**
     * 指定頻度以上のペアが存在しない場合、
     * BPE学習を終了できる。
     */
    public boolean isTrainingComplete(
            long minPairFrequency
    ) {
        return getMostFrequentPair(
                minPairFrequency
        ).isEmpty();
    }

    /**
     * 指定ペアの現在の頻度を取得する。
     */
    public long getPairFrequency(long pair) {
        ensureInitialized();
        validatePair(pair);

        return pairFrequencies.getOrDefault(pair, 0L);
    }

    /**
     * 現在の全ペア頻度を取得する。
     */
    public Map<Long, Long> getPairFrequencies() {
        ensureInitialized();

        return Map.copyOf(pairFrequencies);
    }

    /**
     * 管理中のペア種類数を取得する。
     */
    public int getPairCount() {
        ensureInitialized();

        return pairFrequencies.size();
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "Reducer is not initialized"
            );
        }
    }

    private static void validatePair(long pair) {
        int left = PairFrequencyCounter.leftTokenId(pair);
        int right = PairFrequencyCounter.rightTokenId(pair);

        if (left < 0 || right < 0) {
            throw new IllegalArgumentException(
                    "Invalid pair ID"
            );
        }

        if (SpecialTokens.isSpecialToken(left)
                || SpecialTokens.isSpecialToken(right)) {
            throw new IllegalArgumentException(
                    "Special tokens cannot be included in pairs"
            );
        }
    }

    /**
     * 最頻出ペアの情報。
     */
    public record MostFrequentPair(
            long pairId,
            long frequency
    ) {
        public MostFrequentPair {
            validatePair(pairId);

            if (frequency < 1) {
                throw new IllegalArgumentException(
                        "frequency must be positive"
                );
            }
        }
    }
}