package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.BpeMergeRule;
import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.BpeVocabulary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BPETrainerTest {

    private static BPETrainingConfig config(
            int targetVocabSize,
            long minPairFrequency,
            int workerCount
    ) {
        return new BPETrainingConfig(
                targetVocabSize,
                minPairFrequency,
                workerCount
        );
    }

    @Test
    void learnsUntilTargetVocabularySize() {
        BPETrainer trainer = new BPETrainer(
                config(276, 1, 1)
        );

        BPETrainingResult result = trainer.train(
                List.of(new int[]{1, 2, 1, 2})
        );

        assertEquals(276, result.finalVocabSize());
        assertEquals(2, result.completedMergeCount());

        assertEquals(
                BPETrainingResult.StopReason.TARGET_VOCAB_SIZE_REACHED,
                result.stopReason()
        );

        assertEquals(4L, result.initialTokenCount());
        assertEquals(1L, result.finalTokenCount());
    }

    @Test
    void stopsWhenMinimumPairFrequencyIsNotMet() {
        BPETrainer trainer = new BPETrainer(
                config(300, 2, 1)
        );

        BPETrainingResult result = trainer.train(
                List.of(new int[]{1, 2, 3})
        );

        assertEquals(274, result.finalVocabSize());
        assertEquals(0, result.completedMergeCount());

        assertEquals(
                BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET,
                result.stopReason()
        );

        assertEquals(3L, result.finalTokenCount());
    }

    @Test
    void handlesEmptyTrainingData() {
        BPETrainer trainer = new BPETrainer(
                config(300, 1, 4)
        );

        BPETrainingResult result = trainer.train(List.of());

        assertEquals(274, result.finalVocabSize());
        assertEquals(0L, result.initialTokenCount());
        assertEquals(0L, result.finalTokenCount());

        assertEquals(
                BPETrainingResult.StopReason.MIN_PAIR_FREQUENCY_NOT_MET,
                result.stopReason()
        );
    }

    @Test
    void handlesTargetEqualToInitialVocabularySize() {
        BPETrainer trainer = new BPETrainer(
                config(274, 1, 2)
        );

        BPETrainingResult result = trainer.train(
                List.of(new int[]{1, 2, 3})
        );

        assertEquals(274, result.finalVocabSize());
        assertEquals(0, result.completedMergeCount());
        assertTrue(result.targetVocabSizeReached());
    }

    @Test
    void supportsExistingVocabularyExpansion() {
        BpeVocabulary initialVocabulary =
                new BpeVocabulary(
                        List.of(new BpeMergeRule(1, 2))
                );

        BPETrainer trainer = new BPETrainer(
                config(277, 1, 2),
                initialVocabulary
        );

        BPETrainingResult result = trainer.train(
                List.of(new int[]{274, 3, 274, 3})
        );

        assertEquals(275, result.initialVocabSize());
        assertEquals(277, result.finalVocabSize());
        assertEquals(2, result.completedMergeCount());
        assertEquals(1L, result.finalTokenCount());
    }

    @Test
    void doesNotMergeAcrossSequenceBoundaries() {
        BPETrainer trainer = new BPETrainer(
                config(275, 2, 2)
        );

        BPETrainingResult result = trainer.train(
                List.of(
                        new int[]{1, 2},
                        new int[]{3, 4}
                )
        );

        assertEquals(274, result.finalVocabSize());
        assertEquals(0, result.completedMergeCount());
    }

    @Test
    void supportsParallelWorkers() {
        BPETrainer trainer = new BPETrainer(
                config(276, 1, 4)
        );

        BPETrainingResult result = trainer.train(
                List.of(
                        new int[]{1, 2, 1, 2},
                        new int[]{1, 2, 1, 2},
                        new int[]{1, 2, 1, 2},
                        new int[]{1, 2, 1, 2}
                )
        );

        assertEquals(276, result.finalVocabSize());
        assertEquals(4L, result.finalTokenCount());
        assertEquals(16L, result.initialTokenCount());
    }

    @Test
    void supportsSpecialTokensAsSequenceBoundaries() {
        BPETrainer trainer = new BPETrainer(
                config(276, 1, 1)
        );

        BPETrainingResult result = trainer.train(
                List.of(new int[]{1, 2, 256, 3, 4})
        );

        assertEquals(276, result.finalVocabSize());
        assertEquals(2, result.completedMergeCount());
    }

    @Test
    void rejectsTokenOutsideInitialVocabulary() {
        BPETrainer trainer = new BPETrainer(
                config(300, 1, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.train(
                        List.of(new int[]{1, 274})
                )
        );
    }

    @Test
    void rejectsTargetSmallerThanInitialVocabulary() {
        BpeVocabulary initialVocabulary =
                new BpeVocabulary(
                        List.of(new BpeMergeRule(1, 2))
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainer(
                        config(274, 1, 1),
                        initialVocabulary
                )
        );
    }

    @Test
    void doesNotModifyInputSequences() {
        int[] sequence = {1, 2, 1, 2};

        int[] original = sequence.clone();

        BPETrainer trainer = new BPETrainer(
                config(276, 1, 1)
        );

        trainer.train(List.of(sequence));

        assertArrayEquals(original, sequence);
    }

    @Test
    void handlesEmptySequencesAlongsideNormalSequences() {
        BPETrainer trainer = new BPETrainer(
                config(275, 1, 3)
        );

        BPETrainingResult result = trainer.train(
                List.of(
                        new int[]{},
                        new int[]{1, 2},
                        new int[]{}
                )
        );

        assertEquals(275, result.finalVocabSize());
        assertEquals(2L, result.initialTokenCount());
        assertEquals(1L, result.finalTokenCount());
    }

    @Test
    void rejectsNullSequence() {
        BPETrainer trainer = new BPETrainer(
                config(300, 1, 1)
        );

        assertThrows(
                NullPointerException.class,
                () -> trainer.train(
                        java.util.Arrays.asList((int[]) null)
                )
        );
    }
}