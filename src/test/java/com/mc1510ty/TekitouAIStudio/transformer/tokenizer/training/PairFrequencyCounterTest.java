package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PairFrequencyCounterTest {

    private static long pair(int left, int right) {
        return PairFrequencyCounter.packPair(left, right);
    }

    @Test
    void countsAdjacentPairs() {
        Map<Long, Long> result = PairFrequencyCounter.count(
                List.of(new int[]{1, 2, 1, 2}),
                1
        );

        assertEquals(2L, result.get(pair(1, 2)));
        assertEquals(1L, result.get(pair(2, 1)));
    }

    @Test
    void aggregatesPairsAcrossSequences() {
        Map<Long, Long> result = PairFrequencyCounter.count(
                List.of(
                        new int[]{1, 2, 3},
                        new int[]{1, 2, 4}
                ),
                1
        );

        assertEquals(2L, result.get(pair(1, 2)));
        assertEquals(1L, result.get(pair(2, 3)));
        assertEquals(1L, result.get(pair(2, 4)));

        // 配列の境界をまたぐペアは存在しない
        assertNull(result.get(pair(3, 1)));
    }

    @Test
    void specialTokensBreakPairs() {
        Map<Long, Long> result = PairFrequencyCounter.count(
                List.of(new int[]{
                        10, 11,
                        256,
                        12, 13
                }),
                1
        );

        assertEquals(1L, result.get(pair(10, 11)));
        assertEquals(1L, result.get(pair(12, 13)));

        assertNull(result.get(pair(11, 256)));
        assertNull(result.get(pair(256, 12)));
    }

    @Test
    void parallelAndSequentialResultsAreEqual() {
        List<int[]> sequences = List.of(
                new int[]{1, 2, 3, 4, 5},
                new int[]{1, 2, 3, 2, 1},
                new int[]{5, 4, 3, 2, 1},
                new int[]{10, 20, 30, 40},
                new int[]{1, 2, 1, 2, 1, 2}
        );

        Map<Long, Long> sequential =
                PairFrequencyCounter.count(sequences, 1);

        Map<Long, Long> parallel =
                PairFrequencyCounter.count(sequences, 4);

        assertEquals(sequential, parallel);
    }

    @Test
    void parallelizesSingleLongSequence() {
        int[] tokens = new int[100_000];

        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = i % 10;
        }

        List<int[]> sequences = List.of(tokens);

        Map<Long, Long> sequential =
                PairFrequencyCounter.count(sequences, 1);

        Map<Long, Long> parallel =
                PairFrequencyCounter.count(sequences, 8);

        assertEquals(sequential, parallel);
    }

    @Test
    void returnsEmptyMapForEmptyInput() {
        assertTrue(
                PairFrequencyCounter.count(List.of(), 4).isEmpty()
        );
    }

    @Test
    void returnsEmptyMapForSequencesWithoutPairs() {
        assertTrue(
                PairFrequencyCounter.count(
                        List.of(
                                new int[]{1},
                                new int[]{2},
                                new int[]{}
                        ),
                        4
                ).isEmpty()
        );
    }

    @Test
    void rejectsInvalidWorkerCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PairFrequencyCounter.count(List.of(), 0)
        );
    }

    @Test
    void rejectsNegativeTokenIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PairFrequencyCounter.count(
                        List.of(new int[]{1, -1}),
                        1
                )
        );
    }

    @Test
    void packsAndUnpacksTokenIds() {
        long packed = pair(123456, 789012);

        assertEquals(123456, PairFrequencyCounter.leftTokenId(packed));
        assertEquals(789012, PairFrequencyCounter.rightTokenId(packed));
    }
}