package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PairFrequencyReducerTest {

    private static long pair(int left, int right) {
        return PairFrequencyCounter.packPair(left, right);
    }

    private static PairFrequencyReducer createReducer(
            BPETrainingWorker... workers
    ) {
        PairFrequencyReducer reducer =
                new PairFrequencyReducer();

        reducer.initialize(List.of(workers));

        return reducer;
    }

    @Test
    void initializesAndAggregatesWorkerFrequencies() {
        BPETrainingWorker worker1 =
                new BPETrainingWorker(
                        0,
                        List.of(new int[]{1, 2, 1, 2})
                );

        BPETrainingWorker worker2 =
                new BPETrainingWorker(
                        1,
                        List.of(new int[]{1, 2, 3})
                );

        PairFrequencyReducer reducer =
                createReducer(worker1, worker2);

        assertEquals(3L, reducer.getPairFrequency(pair(1, 2)));
        assertEquals(1L, reducer.getPairFrequency(pair(2, 1)));
        assertEquals(1L, reducer.getPairFrequency(pair(2, 3)));
    }

    @Test
    void selectsMostFrequentPair() {
        PairFrequencyReducer reducer =
                createReducer(
                        new BPETrainingWorker(
                                0,
                                List.of(new int[]{
                                        1, 2, 1, 2, 1, 2,
                                        3, 4
                                })
                        )
                );

        PairFrequencyReducer.MostFrequentPair result =
                reducer.getMostFrequentPair(2).orElseThrow();

        assertEquals(pair(1, 2), result.pairId());
        assertEquals(3L, result.frequency());
    }

    @Test
    void selectsSmallerPairIdWhenFrequenciesAreEqual() {
        PairFrequencyReducer reducer =
                new PairFrequencyReducer();

        reducer.initialize(List.of(
                new BPETrainingWorker(
                        0,
                        List.of(new int[]{2, 3, 2, 3})
                ),
                new BPETrainingWorker(
                        1,
                        List.of(new int[]{1, 4, 1, 4})
                )
        ));

        PairFrequencyReducer.MostFrequentPair result =
                reducer.getMostFrequentPair(1).orElseThrow();

        assertEquals(pair(1, 4), result.pairId());
        assertEquals(2L, result.frequency());
    }

    @Test
    void appliesPositiveFrequencyDelta() {
        PairFrequencyReducer reducer =
                createReducer(
                        new BPETrainingWorker(
                                0,
                                List.of(new int[]{1, 2})
                        )
                );

        reducer.applyDeltas(
                Map.of(pair(3, 4), 5L)
        );

        assertEquals(5L, reducer.getPairFrequency(pair(3, 4)));
    }

    @Test
    void appliesNegativeFrequencyDelta() {
        PairFrequencyReducer reducer =
                createReducer(
                        new BPETrainingWorker(
                                0,
                                List.of(new int[]{1, 2, 1, 2})
                        )
                );

        reducer.applyDeltas(
                Map.of(pair(1, 2), -1L)
        );

        assertEquals(1L, reducer.getPairFrequency(pair(1, 2)));
    }

    @Test
    void removesPairWhenFrequencyBecomesZero() {
        PairFrequencyReducer reducer =
                createReducer(
                        new BPETrainingWorker(
                                0,
                                List.of(new int[]{1, 2})
                        )
                );

        reducer.applyDeltas(
                Map.of(pair(1, 2), -1L)
        );

        assertEquals(0L, reducer.getPairFrequency(pair(1, 2)));
        assertEquals(0, reducer.getPairCount());
    }

    @Test
    void appliesWorkerMergeResult() {
        BPETrainingWorker worker =
                new BPETrainingWorker(
                        0,
                        List.of(new int[]{1, 2, 3})
                );

        PairFrequencyReducer reducer =
                createReducer(worker);

        BPETrainingWorker.MergeResult result =
                worker.mergePair(pair(2, 3), 274);

        reducer.applyWorkerResult(result);

        assertEquals(0L, reducer.getPairFrequency(pair(2, 3)));
        assertEquals(1L, reducer.getPairFrequency(pair(1, 274)));
        assertEquals(0L, reducer.getPairFrequency(pair(1, 2)));
    }

    @Test
    void respectsMinimumPairFrequency() {
        PairFrequencyReducer reducer =
                createReducer(
                        new BPETrainingWorker(
                                0,
                                List.of(new int[]{1, 2, 3})
                        )
                );

        assertTrue(reducer.getMostFrequentPair(2).isEmpty());
        assertFalse(reducer.isTrainingComplete(1));
        assertTrue(reducer.isTrainingComplete(2));
    }

    @Test
    void rejectsNegativeResultingFrequencyWithoutPartialUpdate() {
        PairFrequencyReducer reducer =
                createReducer(
                        new BPETrainingWorker(
                                0,
                                List.of(new int[]{1, 2})
                        )
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> reducer.applyDeltas(
                        Map.of(
                                pair(1, 2), -2L,
                                pair(3, 4), 5L
                        )
                )
        );

        assertEquals(1L, reducer.getPairFrequency(pair(1, 2)));
        assertEquals(0L, reducer.getPairFrequency(pair(3, 4)));
    }

    @Test
    void rejectsDuplicateWorkerIds() {
        PairFrequencyReducer reducer =
                new PairFrequencyReducer();

        assertThrows(
                IllegalArgumentException.class,
                () -> reducer.initialize(
                        List.of(
                                new BPETrainingWorker(
                                        0,
                                        List.of(new int[]{1, 2})
                                ),
                                new BPETrainingWorker(
                                        0,
                                        List.of(new int[]{3, 4})
                                )
                        )
                )
        );

        assertFalse(reducer.isInitialized());
    }

    @Test
    void rejectsMultipleInitialization() {
        PairFrequencyReducer reducer =
                createReducer(
                        new BPETrainingWorker(
                                0,
                                List.of(new int[]{1, 2})
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () -> reducer.initialize(List.of())
        );
    }

    @Test
    void returnsImmutableFrequencySnapshot() {
        PairFrequencyReducer reducer =
                createReducer(
                        new BPETrainingWorker(
                                0,
                                List.of(new int[]{1, 2})
                        )
                );

        Map<Long, Long> frequencies =
                reducer.getPairFrequencies();

        assertThrows(
                UnsupportedOperationException.class,
                () -> frequencies.put(pair(3, 4), 5L)
        );
    }

    @Test
    void rejectsAccessBeforeInitialization() {
        PairFrequencyReducer reducer =
                new PairFrequencyReducer();

        assertThrows(
                IllegalStateException.class,
                reducer::getPairFrequencies
        );

        assertThrows(
                IllegalStateException.class,
                () -> reducer.getMostFrequentPair(1)
        );
    }
}