
package com.mc1510ty.TekitouAIStudio.transformer.tokenizer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ByteLevelBPETest {

    @Test
    void testInitialVocabSize() {

        ByteLevelBPE tokenizer = new ByteLevelBPE();

        assertEquals(274, tokenizer.vocabSize());
    }

    @Test
    void testByteFallbackRoundTrip() {

        ByteLevelBPE tokenizer = new ByteLevelBPE();

        String text = "Hello, 世界！Minecraft 1.21 🙂\n";

        int[] ids = tokenizer.encodeText(text);

        assertEquals(text, tokenizer.decodeText(ids));

        for (int id : ids) {
            assertTrue(id >= 0 && id < 256);
        }
    }

    @Test
    void testMergeRules() {

        ByteLevelBPE tokenizer = new ByteLevelBPE(
                List.of(
                        new BpeMergeRule('h', 'e'),
                        new BpeMergeRule('l', 'l'),
                        new BpeMergeRule(274, 275),
                        new BpeMergeRule(276, 'o')
                )
        );

        int[] ids = tokenizer.encodeText("hello");

        assertArrayEquals(new int[]{277}, ids);

        assertEquals("hello", tokenizer.decodeText(ids));

        assertEquals(278, tokenizer.vocabSize());
    }

    @Test
    void testMergeAcrossWhitespace() {

        ByteLevelBPE tokenizer = new ByteLevelBPE(
                List.of(
                        new BpeMergeRule('a', ' ')
                )
        );

        assertArrayEquals(
                new int[]{274, 'b'},
                tokenizer.encodeText("a b")
        );

        assertEquals(
                "a b",
                tokenizer.decodeText(
                        tokenizer.encodeText("a b")
                )
        );
    }

    @Test
    void testJapaneseRoundTrip() {

        ByteLevelBPE tokenizer = new ByteLevelBPE();

        String text = "こんにちは。今日はいい天気ですね。";

        assertEquals(
                text,
                tokenizer.decodeText(
                        tokenizer.encodeText(text)
                )
        );
    }

    @Test
    void testEmptyText() {

        ByteLevelBPE tokenizer = new ByteLevelBPE();

        assertArrayEquals(
                new int[0],
                tokenizer.encodeText("")
        );

        assertEquals(
                "",
                tokenizer.decodeText(new int[0])
        );
    }

    @Test
    void testRejectSpecialTokenId() {

        ByteLevelBPE tokenizer = new ByteLevelBPE();

        assertThrows(
                IllegalArgumentException.class,
                () -> tokenizer.decodeText(
                        new int[]{SpecialTokens.MESSAGE_END_ID}
                )
        );
    }

    @Test
    void testRejectInvalidTokenId() {

        ByteLevelBPE tokenizer = new ByteLevelBPE();

        assertThrows(
                IllegalArgumentException.class,
                () -> tokenizer.decodeText(new int[]{274})
        );
    }

    @Test
    void testRejectSpecialTokenAsMergeInput() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ByteLevelBPE(
                        List.of(
                                new BpeMergeRule(
                                        SpecialTokens.MESSAGE_END_ID,
                                        'a'
                                )
                        )
                )
        );
    }

    @Test
    void testRejectForwardReference() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ByteLevelBPE(
                        List.of(
                                new BpeMergeRule(274, 'a')
                        )
                )
        );
    }

    @Test
    void testRejectDuplicateMerge() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ByteLevelBPE(
                        List.of(
                                new BpeMergeRule('a', 'b'),
                                new BpeMergeRule('a', 'b')
                        )
                )
        );
    }

    @Test
    void testVocabularyExpansion() {

        ByteLevelBPE original = new ByteLevelBPE(
                List.of(
                        new BpeMergeRule('a', 'b')
                )
        );

        ByteLevelBPE expanded =
                original.withAdditionalMerges(
                        List.of(
                                new BpeMergeRule(274, 'c')
                        )
                );

        assertEquals(275, original.vocabSize());
        assertEquals(276, expanded.vocabSize());

        assertArrayEquals(
                new int[]{274, 'c'},
                original.encodeText("abc")
        );

        assertArrayEquals(
                new int[]{275},
                expanded.encodeText("abc")
        );

        assertArrayEquals(
                new int[]{274},
                original.encodeText("ab")
        );

        assertArrayEquals(
                new int[]{274},
                expanded.encodeText("ab")
        );
    }

    @Test
    void testInvalidUnicodeInput() {

        ByteLevelBPE tokenizer = new ByteLevelBPE();

        String invalid = new String(
                new char[]{(char) 0xD800}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> tokenizer.encodeText(invalid)
        );
    }

    @Test
    void testInvalidUtf8Decode() {

        ByteLevelBPE tokenizer = new ByteLevelBPE();

        assertThrows(
                IllegalArgumentException.class,
                () -> tokenizer.decodeText(new int[]{0xC3})
        );
    }
}
