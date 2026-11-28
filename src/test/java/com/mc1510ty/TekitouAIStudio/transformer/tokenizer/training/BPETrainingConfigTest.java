
package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.SpecialTokens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BPETrainingConfigTest {

    @Test
    void testDefaultConfig() {

        BPETrainingConfig config =
                BPETrainingConfig.defaults();

        assertEquals(100_000, config.targetVocabSize());
        assertEquals(2, config.minPairFrequency());

        assertTrue(config.workerCount() >= 1);
    }

    @Test
    void testInitialMergeCount() {

        BPETrainingConfig config =
                BPETrainingConfig.defaults();

        assertEquals(
                99_726,
                config.getInitialMergeCount()
        );
    }

    @Test
    void testAdditionalMergeCount() {

        BPETrainingConfig config =
                BPETrainingConfig.defaults();

        assertEquals(
                70_000,
                config.getAdditionalMergeCount(30_000)
        );
    }

    @Test
    void testNoAdditionalMergeNeeded() {

        BPETrainingConfig config =
                new BPETrainingConfig(
                        30_000,
                        2,
                        4
                );

        assertEquals(
                0,
                config.getAdditionalMergeCount(30_000)
        );
    }

    @Test
    void testRejectInvalidVocabSize() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingConfig(
                        SpecialTokens.FIRST_BPE_TOKEN_ID - 1,
                        2,
                        4
                )
        );
    }

    @Test
    void testRejectInvalidFrequency() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingConfig(
                        100_000,
                        0,
                        4
                )
        );
    }

    @Test
    void testRejectInvalidWorkerCount() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new BPETrainingConfig(
                        100_000,
                        2,
                        0
                )
        );
    }

    @Test
    void testRejectCurrentVocabLargerThanTarget() {

        BPETrainingConfig config =
                BPETrainingConfig.defaults();

        assertThrows(
                IllegalArgumentException.class,
                () -> config.getAdditionalMergeCount(100_001)
        );
    }
}
