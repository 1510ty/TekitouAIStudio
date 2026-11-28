package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.BpeMergeRule;
import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.BpeVocabulary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BPETrainingResultTest {

    private static BpeVocabulary createVocabulary(
            int mergeCount
    ) {
        List<BpeMergeRule> rules =
                new ArrayList<>(mergeCount);

        for (int i = 0; i < mergeCount; i++) {
            int leftTokenId = i == 0 ? 0 : 273 + i;

            rules.add(
                    new BpeMergeRule(leftTokenId, 0)
            );
        }

        return new BpeVocabulary(rules);
    }

    @Test
    void createsSuccessfulTrainingResult() {
        BpeVocabulary vocabulary = createVocabulary(3);

        BPETrainingResult result = new BPETrainingResult(
                vocabulary,
                274,
                300,
                1000,
                900,
                BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET
        );

        assertSame(vocabulary, result.vocabulary());
        assertEquals(274, result.initialVocabSize());
        assertEquals(300, result.targetVocabSize());
        assertEquals(277, result.finalVocabSize());
        assertEquals(1000L, result.initialTokenCount());
        assertEquals(900L, result.finalTokenCount());
    }

    @Test
    void calculatesCompletedMergeCount() {
        BPETrainingResult result = new BPETrainingResult(
                createVocabulary(10),
                274,
                300,
                1000,
                800,
                BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET
        );

        assertEquals(10, result.completedMergeCount());
    }

    @Test
    void calculatesRemainingMergeCount() {
        BPETrainingResult result = new BPETrainingResult(
                createVocabulary(10),
                274,
                300,
                1000,
                800,
                BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET
        );

        assertEquals(16, result.remainingMergeCount());
    }

    @Test
    void detectsTargetVocabularyReached() {
        BPETrainingResult result = new BPETrainingResult(
                createVocabulary(26),
                274,
                300,
                1000,
                700,
                BPETrainingResult.StopReason.TARGET_VOCAB_SIZE_REACHED
        );

        assertTrue(result.targetVocabSizeReached());
        assertEquals(300, result.finalVocabSize());
        assertEquals(0, result.remainingMergeCount());
    }

    @Test
    void detectsMinimumPairFrequencyStop() {
        BPETrainingResult result = new BPETrainingResult(
                createVocabulary(10),
                274,
                300,
                1000,
                800,
                BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET
        );

        assertFalse(result.targetVocabSizeReached());
    }

    @Test
    void supportsTrainingWithoutAnyMerges() {
        BPETrainingResult result = new BPETrainingResult(
                createVocabulary(0),
                274,
                100_000,
                100,
                100,
                BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET
        );

        assertEquals(0, result.completedMergeCount());
        assertEquals(99_726, result.remainingMergeCount());
    }

    @Test
    void rejectsNullVocabulary() {
        assertThrows(
                NullPointerException.class,
                () -> new BPETrainingResult(
                        null,
                        274,
                        300,
                        100,
                        90,
                        BPETrainingResult.StopReason.TARGET_VOCAB_SIZE_REACHED
                )
        );
    }

    @Test
    void rejectsInitialVocabularyBelowMinimum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingResult(
                        createVocabulary(0),
                        273,
                        300,
                        100,
                        90,
                        BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET
                )
        );
    }

    @Test
    void rejectsTargetSmallerThanInitialVocabulary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingResult(
                        createVocabulary(0),
                        300,
                        274,
                        100,
                        90,
                        BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET
                )
        );
    }

    @Test
    void rejectsVocabularyExceedingTarget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingResult(
                        createVocabulary(27),
                        274,
                        300,
                        100,
                        90,
                        BPETrainingResult.StopReason.TARGET_VOCAB_SIZE_REACHED
                )
        );
    }

    @Test
    void rejectsNegativeTokenCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingResult(
                        createVocabulary(1),
                        274,
                        300,
                        -1,
                        0,
                        BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET
                )
        );
    }

    @Test
    void rejectsFinalTokenCountGreaterThanInitial() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingResult(
                        createVocabulary(1),
                        274,
                        300,
                        100,
                        101,
                        BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET
                )
        );
    }

    @Test
    void rejectsIncorrectTargetReachedReason() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingResult(
                        createVocabulary(10),
                        274,
                        300,
                        100,
                        90,
                        BPETrainingResult.StopReason.TARGET_VOCAB_SIZE_REACHED
                )
        );
    }

    @Test
    void rejectsIncorrectMinimumFrequencyStopReason() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingResult(
                        createVocabulary(26),
                        274,
                        300,
                        100,
                        90,
                        BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET
                )
        );
    }
}