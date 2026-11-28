package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PairOccurrenceIndexTest {

    private static long pair(int left, int right) {
        return PairFrequencyCounter.packPair(left, right);
    }

    @Test
    void buildsInitialPairFrequencies() {
        PairOccurrenceIndex index = new PairOccurrenceIndex(
                List.of(new int[]{1, 2, 1, 2})
        );

        Map<Long, Long> frequencies = index.getPairFrequencies();

        assertEquals(2L, frequencies.get(pair(1, 2)));
        assertEquals(1L, frequencies.get(pair(2, 1)));
    }

    @Test
    void mergesNonOverlappingOccurrences() {
        PairOccurrenceIndex index = new PairOccurrenceIndex(
                List.of(new int[]{1, 2, 1, 2})
        );

        long mergedCount = index.mergePair(pair(1, 2), 274);

        assertEquals(2L, mergedCount);

        List<int[]> sequences = index.getSequences();

        assertArrayEquals(
                new int[]{274, 274},
                sequences.getFirst()
        );

        assertEquals(2L, index.getTokenCount());

        Map<Long, Long> frequencies = index.getPairFrequencies();

        assertEquals(1L, frequencies.get(pair(274, 274)));
        assertFalse(frequencies.containsKey(pair(1, 2)));
        assertFalse(frequencies.containsKey(pair(2, 1)));
    }

    @Test
    void handlesOverlappingOccurrences() {
        PairOccurrenceIndex index = new PairOccurrenceIndex(
                List.of(new int[]{1, 1, 1})
        );

        long mergedCount = index.mergePair(pair(1, 1), 274);

        // 左からマージするため、重複する2箇所のうち1箇所だけが対象
        assertEquals(1L, mergedCount);

        assertArrayEquals(
                new int[]{274, 1},
                index.getSequences().getFirst()
        );

        assertEquals(
                1L,
                index.getPairFrequency(pair(274, 1))
        );
    }

    @Test
    void updatesOnlyAffectedPairFrequencies() {
        PairOccurrenceIndex index = new PairOccurrenceIndex(
                List.of(new int[]{1, 2, 3})
        );

        Map<Long, Long> initialDeltas =
                index.drainFrequencyDeltas();

        assertTrue(initialDeltas.isEmpty());

        index.mergePair(pair(2, 3), 274);

        Map<Long, Long> deltas =
                index.drainFrequencyDeltas();

        assertEquals(-1L, deltas.get(pair(1, 2)));
        assertEquals(-1L, deltas.get(pair(2, 3)));
        assertEquals(1L, deltas.get(pair(1, 274)));
    }

    @Test
    void doesNotCreatePairsAcrossSequenceBoundaries() {
        PairOccurrenceIndex index = new PairOccurrenceIndex(
                List.of(
                        new int[]{1, 2},
                        new int[]{3, 4}
                )
        );

        Map<Long, Long> frequencies = index.getPairFrequencies();

        assertEquals(1L, frequencies.get(pair(1, 2)));
        assertEquals(1L, frequencies.get(pair(3, 4)));

        assertFalse(frequencies.containsKey(pair(2, 3)));
    }

    @Test
    void specialTokensBreakPairs() {
        PairOccurrenceIndex index = new PairOccurrenceIndex(
                List.of(new int[]{10, 256, 12})
        );

        assertTrue(index.getPairFrequencies().isEmpty());
    }

    @Test
    void returnsZeroWhenPairDoesNotExistLocally() {
        PairOccurrenceIndex index = new PairOccurrenceIndex(
                List.of(new int[]{1, 2, 3})
        );

        assertEquals(
                0L,
                index.mergePair(pair(4, 5), 274)
        );

        assertArrayEquals(
                new int[]{1, 2, 3},
                index.getSequences().getFirst()
        );
    }

    @Test
    void rejectsNonIncreasingTokenIds() {
        PairOccurrenceIndex index = new PairOccurrenceIndex(
                List.of(new int[]{1, 2})
        );

        index.mergePair(pair(1, 2), 274);

        assertThrows(
                IllegalArgumentException.class,
                () -> index.mergePair(pair(274, 274), 274)
        );
    }

    @Test
    void supportsEmptySequences() {
        PairOccurrenceIndex index = new PairOccurrenceIndex(
                List.of(
                        new int[]{},
                        new int[]{1},
                        new int[]{}
                )
        );

        assertEquals(3, index.getSequenceCount());
        assertEquals(1L, index.getTokenCount());
        assertTrue(index.getPairFrequencies().isEmpty());
    }

    @Test
    void returnsIndependentSequenceCopies() {
        PairOccurrenceIndex index = new PairOccurrenceIndex(
                List.of(new int[]{1, 2, 3})
        );

        List<int[]> first = index.getSequences();
        first.getFirst()[0] = 999;

        assertArrayEquals(
                new int[]{1, 2, 3},
                index.getSequences().getFirst()
        );
    }
}