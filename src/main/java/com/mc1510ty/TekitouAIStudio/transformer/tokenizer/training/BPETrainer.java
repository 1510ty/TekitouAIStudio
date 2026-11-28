package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.BpeMergeRule;
import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.BpeVocabulary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class BPETrainer {

    private final BPETrainingConfig config;
    private final BpeVocabulary initialVocabulary;

    public BPETrainer(BPETrainingConfig config) {
        this(config, new BpeVocabulary(List.of()));
    }

    /**
     * 既存の語彙を引き継いで学習する場合に使用する。
     */
    public BPETrainer(
            BPETrainingConfig config,
            BpeVocabulary initialVocabulary
    ) {
        this.config = Objects.requireNonNull(
                config,
                "config must not be null"
        );

        this.initialVocabulary = Objects.requireNonNull(
                initialVocabulary,
                "initialVocabulary must not be null"
        );

        if (config.targetVocabSize() < initialVocabulary.vocabSize()) {
            throw new IllegalArgumentException(
                    "Target vocabulary size is smaller than the initial vocabulary size"
            );
        }
    }

    /**
     * BPE学習を実行する。
     *
     * 各int[]は独立したSequenceとして扱われる。
     */
    public BPETrainingResult train(List<int[]> sequences) {
        Objects.requireNonNull(
                sequences,
                "sequences must not be null"
        );

        int initialVocabSize = initialVocabulary.vocabSize();

        List<int[]> validSequences = new ArrayList<>();
        long initialTokenCount = 0;

        // 入力データの検証
        for (int sequenceIndex = 0;
             sequenceIndex < sequences.size();
             sequenceIndex++) {

            int[] sequence = Objects.requireNonNull(
                    sequences.get(sequenceIndex),
                    "sequences[" + sequenceIndex + "] must not be null"
            );

            initialTokenCount = Math.addExact(
                    initialTokenCount,
                    sequence.length
            );

            for (int tokenId : sequence) {
                if (tokenId < 0 || tokenId >= initialVocabSize) {
                    throw new IllegalArgumentException(
                            "Token ID is outside the initial vocabulary: "
                                    + tokenId
                    );
                }
            }

            if (sequence.length > 0) {
                validSequences.add(sequence);
            }
        }

        // データをワーカーに分配
        List<BPETrainingWorker> workers =
                createWorkers(validSequences);

        // 全ワーカーの初期頻度を集約
        PairFrequencyReducer reducer =
                new PairFrequencyReducer();

        reducer.initialize(workers);

        List<BpeMergeRule> additionalMergeRules =
                new ArrayList<>();

        int currentVocabSize = initialVocabSize;

        BPETrainingResult.StopReason stopReason =
                currentVocabSize == config.targetVocabSize()
                        ? BPETrainingResult.StopReason.TARGET_VOCAB_SIZE_REACHED
                        : BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET;

        ExecutorService executor = workers.size() > 1
                ? Executors.newFixedThreadPool(workers.size())
                : null;

        try {
            // 目標語彙数に達するまでマージを繰り返す
            while (currentVocabSize < config.targetVocabSize()) {

                Optional<PairFrequencyReducer.MostFrequentPair> selected =
                        reducer.getMostFrequentPair(
                                config.minPairFrequency()
                        );

                // 最小出現頻度を満たすペアが存在しない
                if (selected.isEmpty()) {
                    stopReason =
                            BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET;
                    break;
                }

                long pair = selected.get().pairId();

                int leftTokenId =
                        PairFrequencyCounter.leftTokenId(pair);

                int rightTokenId =
                        PairFrequencyCounter.rightTokenId(pair);

                // 新しいトークンIDを割り当てる
                int newTokenId = currentVocabSize;

                // 全ワーカーで同じペアをマージ
                List<BPETrainingWorker.MergeResult> results =
                        mergeWorkers(
                                workers,
                                pair,
                                newTokenId,
                                executor
                        );

                long totalMergedCount = 0;

                Map<Long, Long> combinedDeltas =
                        new HashMap<>();

                // ワーカーの結果を統合
                for (BPETrainingWorker.MergeResult result : results) {

                    totalMergedCount = Math.addExact(
                            totalMergedCount,
                            result.mergedCount()
                    );

                    for (Map.Entry<Long, Long> entry :
                            result.frequencyDeltas().entrySet()) {

                        combinedDeltas.merge(
                                entry.getKey(),
                                entry.getValue(),
                                Math::addExact
                        );
                    }
                }

                if (totalMergedCount == 0) {
                    throw new IllegalStateException(
                            "Selected pair was not merged by any worker"
                    );
                }

                // Reducerに頻度差分を反映
                reducer.applyDeltas(combinedDeltas);

                // マージルールを記録
                additionalMergeRules.add(
                        new BpeMergeRule(
                                leftTokenId,
                                rightTokenId
                        )
                );

                currentVocabSize++;

                if (currentVocabSize == config.targetVocabSize()) {
                    stopReason =
                            BPETrainingResult.StopReason.TARGET_VOCAB_SIZE_REACHED;
                }
            }

            // 全ワーカーの最終トークン数を集計
            long finalTokenCount = 0;

            for (BPETrainingWorker worker : workers) {
                finalTokenCount = Math.addExact(
                        finalTokenCount,
                        worker.getTokenCount()
                );
            }

            // 既存語彙の後ろに新しいマージルールを追加
            BpeVocabulary resultVocabulary =
                    additionalMergeRules.isEmpty()
                            ? initialVocabulary
                            : initialVocabulary.withAdditionalMerges(
                            additionalMergeRules
                    );

            return new BPETrainingResult(
                    resultVocabulary,
                    initialVocabSize,
                    config.targetVocabSize(),
                    initialTokenCount,
                    finalTokenCount,
                    stopReason
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "BPE training was interrupted",
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
                    "BPE worker execution failed",
                    cause
            );

        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * Sequenceをトークン数に応じてワーカーに分配する。
     *
     * 長いSequenceから順に、現在の担当トークン数が
     * 最も少ないワーカーへ割り当てる。
     */
    private List<BPETrainingWorker> createWorkers(
            List<int[]> sequences
    ) {
        int workerCount = Math.min(
                config.workerCount(),
                Math.max(1, sequences.size())
        );

        List<WorkerPartition> partitions =
                new ArrayList<>(workerCount);

        PriorityQueue<WorkerPartition> queue =
                new PriorityQueue<>(
                        Comparator
                                .comparingLong(
                                        (WorkerPartition p) -> p.tokenCount
                                )
                                .thenComparingInt(p -> p.workerId)
                );

        for (int i = 0; i < workerCount; i++) {
            WorkerPartition partition =
                    new WorkerPartition(i);

            partitions.add(partition);
            queue.add(partition);
        }

        List<IndexedSequence> ordered =
                new ArrayList<>(sequences.size());

        for (int i = 0; i < sequences.size(); i++) {
            ordered.add(
                    new IndexedSequence(i, sequences.get(i))
            );
        }

        ordered.sort(
                Comparator
                        .comparingInt(
                                (IndexedSequence s) -> s.tokens.length
                        )
                        .reversed()
                        .thenComparingInt(s -> s.originalIndex)
        );

        for (IndexedSequence sequence : ordered) {

            WorkerPartition partition = queue.remove();

            partition.sequences.add(sequence.tokens);

            partition.tokenCount += sequence.tokens.length;

            queue.add(partition);
        }

        List<BPETrainingWorker> workers =
                new ArrayList<>(workerCount);

        for (WorkerPartition partition : partitions) {
            workers.add(
                    new BPETrainingWorker(
                            partition.workerId,
                            partition.sequences
                    )
            );
        }

        return workers;
    }

    /**
     * 全ワーカーで指定ペアをマージする。
     */
    private static List<BPETrainingWorker.MergeResult> mergeWorkers(
            List<BPETrainingWorker> workers,
            long pair,
            int newTokenId,
            ExecutorService executor
    ) throws InterruptedException, ExecutionException {

        List<BPETrainingWorker.MergeResult> results =
                new ArrayList<>(workers.size());

        // ワーカーが1つなら直接実行
        if (executor == null) {
            for (BPETrainingWorker worker : workers) {
                results.add(
                        worker.mergePair(pair, newTokenId)
                );
            }

            return results;
        }

        List<Callable<BPETrainingWorker.MergeResult>> tasks =
                new ArrayList<>(workers.size());

        for (BPETrainingWorker worker : workers) {
            tasks.add(
                    () -> worker.mergePair(pair, newTokenId)
            );
        }

        List<Future<BPETrainingWorker.MergeResult>> futures =
                executor.invokeAll(tasks);

        for (Future<BPETrainingWorker.MergeResult> future : futures) {
            results.add(future.get());
        }

        return results;
    }

    private record IndexedSequence(
            int originalIndex,
            int[] tokens
    ) {
    }

    private static final class WorkerPartition {

        private final int workerId;
        private final List<int[]> sequences =
                new ArrayList<>();

        private long tokenCount;

        private WorkerPartition(int workerId) {
            this.workerId = workerId;
        }
    }
}