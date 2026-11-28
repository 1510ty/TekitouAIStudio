
package com.mc1510ty.TekitouAIStudio.transformer.tokenizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpecialTokensTest {

    @Test
    void testTokenCount() {

        assertEquals(
                18,
                SpecialTokens.SPECIAL_TOKEN_COUNT
        );

        assertEquals(
                18,
                SpecialTokens.getAllIds().size()
        );
    }

    @Test
    void testIdDefinitions() {

        assertEquals(256, SpecialTokens.TURN_ASSISTANT_END_ID);
        assertEquals(257, SpecialTokens.MESSAGE_END_ID);
        assertEquals(258, SpecialTokens.LINE_BREAK_ID);
        assertEquals(259, SpecialTokens.TURN_USER_START_ID);
        assertEquals(260, SpecialTokens.TURN_USER_END_ID);
        assertEquals(261, SpecialTokens.TURN_ASSISTANT_START_ID);
        assertEquals(262, SpecialTokens.TURN_CONSOLE_START_ID);
        assertEquals(263, SpecialTokens.TURN_CONSOLE_END_ID);
        assertEquals(264, SpecialTokens.MESSAGE_START_ID);
        assertEquals(265, SpecialTokens.DRAFT_START_ID);
        assertEquals(266, SpecialTokens.DRAFT_END_ID);
        assertEquals(267, SpecialTokens.DRAFT_PUSH_ID);
        assertEquals(268, SpecialTokens.SEARCH_START_ID);
        assertEquals(269, SpecialTokens.SEARCH_END_ID);
        assertEquals(270, SpecialTokens.CONSOLE_START_ID);
        assertEquals(271, SpecialTokens.CONSOLE_END_ID);
        assertEquals(272, SpecialTokens.TURN_SEARCH_START_ID);
        assertEquals(273, SpecialTokens.TURN_SEARCH_END_ID);
    }

    @Test
    void testSpecialTokenDetection() {

        assertTrue(
                SpecialTokens.isSpecialToken(256)
        );

        assertTrue(
                SpecialTokens.isSpecialToken(273)
        );

        assertFalse(
                SpecialTokens.isSpecialToken(255)
        );

        assertFalse(
                SpecialTokens.isSpecialToken(274)
        );

        assertFalse(
                SpecialTokens.isSpecialToken(1000)
        );
    }

    @Test
    void testAllIds() {

        for (int i = 0; i < SpecialTokens.SPECIAL_TOKEN_COUNT; i++) {

            int id = SpecialTokens.getAllIds().get(i);

            assertEquals(
                    SpecialTokens.FIRST_SPECIAL_TOKEN_ID + i,
                    id
            );

            assertTrue(
                    SpecialTokens.isSpecialToken(id)
            );
        }
    }

    @Test
    void testVocabularyBoundaries() {

        assertEquals(256, SpecialTokens.BYTE_TOKEN_COUNT);

        assertEquals(256, SpecialTokens.FIRST_SPECIAL_TOKEN_ID);

        assertEquals(273, SpecialTokens.LAST_SPECIAL_TOKEN_ID);

        assertEquals(274, SpecialTokens.FIRST_BPE_TOKEN_ID);
    }
}
