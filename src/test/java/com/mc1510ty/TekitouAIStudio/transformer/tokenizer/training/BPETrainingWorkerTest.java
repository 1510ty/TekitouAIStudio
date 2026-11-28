package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BPETrainingWorkerTest {

    private static long pair(int left, int right) {
        return PairFrequencyCounter.packPair(left, right);
    }

    @Test
    void initializesWorker() {
        BPETrainingWorker worker = new BPETrainingWorker(
                0,
                List.of(new int[]{1, 2, 3})
        );

        assertEquals(0, worker.getWorkerId());
        assertEquals(3L, worker.getTokenCount());
    }

    @Test
    void returnsInitialPairFrequencies() {
        BPETrainingWorker worker = new BPETrainingWorker(
                0,
                List.of(new int[]{1, 2, 1, 2})
        );

        Map<Long, Long> frequencies =
                worker.getInitialPairFrequencies();

        assertEquals(2L, frequencies.get(pair(1, 2)));
        assertEquals(1L, frequencies.get(pair(2, 1)));
    }

    @Test
    void mergesPairsAndReturnsResult() {
        BPETrainingWorker worker = new BPETrainingWorker(
                0,
                List.of(new int[]{1, 2, 3})
        );

        BPETrainingWorker.MergeResult result =
                worker.mergePair(pair(2, 3), 274);

        assertEquals(1L, result.mergedCount());

        assertEquals(
                -1L,
                result.frequencyDeltas().get(pair(2, 3))
        );

        assertEquals(
                1L,
                result.frequencyDeltas().get(pair(1, 274))
        );
    }

    @Test
    void returnsZeroWhenPairDoesNotExist() {
        BPETrainingWorker worker = new BPETrainingWorker(
                0,
                List.of(new int[]{1, 2, 3})
        );

        BPETrainingWorker.MergeResult result =
                worker.mergePair(pair(4, 5), 274);

        assertEquals(0L, result.mergedCount());
        assertTrue(result.frequencyDeltas().isEmpty());
    }

    @Test
    void updatesTokenCountAfterMerge() {
        BPETrainingWorker worker = new BPETrainingWorker(
                0,
                List.of(new int[]{1, 2, 3, 4})
        );

        worker.mergePair(pair(2, 3), 274);

        assertEquals(3L, worker.getTokenCount());
    }

    @Test
    void processesMultipleOccurrences() {
        BPETrainingWorker worker = new BPETrainingWorker(
                0,
                List.of(new int[]{1, 2, 1, 2})
        );

        BPETrainingWorker.MergeResult result =
                worker.mergePair(pair(1, 2), 274);

        assertEquals(2L, result.mergedCount());
        assertEquals(2L, worker.getTokenCount());
    }

    @Test
    void maintainsIndependentWorkerStates() {
        BPETrainingWorker worker1 = new BPETrainingWorker(
                0,
                List.of(new int[]{1, 2, 3})
        );

        BPETrainingWorker worker2 = new BPETrainingWorker(
                1,
                List.of(new int[]{1, 2, 4})
        );

        worker1.mergePair(pair(1, 2), 274);

        assertEquals(2L, worker1.getTokenCount());
        assertEquals(3L, worker2.getTokenCount());

        assertEquals(
                1L,
                worker2.getInitialPairFrequencies().get(pair(1, 2))
        );
    }

    @Test
    void returnsImmutableFrequencyMap() {
        BPETrainingWorker worker = new BPETrainingWorker(
                0,
                List.of(new int[]{1, 2, 3})
        );

        Map<Long, Long> frequencies =
                worker.getInitialPairFrequencies();

        assertThrows(
                UnsupportedOperationException.class,
                () -> frequencies.put(pair(4, 5), 1L)
        );
    }

    @Test
    void mergeResultContainsImmutableMap() {
        BPETrainingWorker.MergeResult result =
                new BPETrainingWorker.MergeResult(
                        1,
                        Map.of(pair(1, 2), -1L)
                );

        assertThrows(
                UnsupportedOperationException.class,
                () -> result.frequencyDeltas().put(
                        pair(3, 4),
                        1L
                )
        );
    }

    @Test
    void rejectsNegativeWorkerId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingWorker(
                        -1,
                        List.of(new int[]{1, 2})
                )
        );
    }
}