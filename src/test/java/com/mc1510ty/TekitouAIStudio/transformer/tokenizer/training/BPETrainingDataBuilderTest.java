package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.BpeMergeRule;
import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.BpeVocabulary;
import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.ByteLevelBPE;
import com.mc1510ty.TekitouAIStudio.transformer.training.data.TrainingData;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BPETrainingDataBuilderTest {

    private static BPETrainingDataBuilder createBuilder() {
        return new BPETrainingDataBuilder(
                new ByteLevelBPE(
                        new BpeVocabulary(List.of()).getMergeRules()
                )
        );
    }

    private static TrainingData.Content text(
            String value,
            Boolean loss
    ) {
        return new TrainingData.Content(
                "text",
                value,
                loss
        );
    }

    @Test
    void buildsDocumentText() {
        var builder = createBuilder();

        List<int[]> result = builder.buildFromRecords(
                List.of(
                        new TrainingData.Document(
                                true,
                                List.of(text("hello", null))
                        )
                )
        );

        assertEquals(1, result.size());

        assertArrayEquals(
                new int[]{104, 101, 108, 108, 111},
                result.getFirst()
        );
    }

    @Test
    void includesLossExcludedContent() {
        var builder = createBuilder();

        List<int[]> result = builder.buildFromRecords(
                List.of(
                        new TrainingData.Document(
                                false,
                                List.of(text("input", null))
                        )
                )
        );

        assertEquals(1, result.size());

        assertArrayEquals(
                new int[]{105, 110, 112, 117, 116},
                result.getFirst()
        );
    }

    @Test
    void separatesSequencesWhenLossChanges() {
        var builder = createBuilder();

        List<int[]> result = builder.buildFromRecords(
                List.of(
                        new TrainingData.Document(
                                true,
                                List.of(
                                        text("A", null),
                                        text("B", false),
                                        text("C", false),
                                        text("D", true)
                                )
                        )
                )
        );

        assertEquals(3, result.size());

        assertArrayEquals(new int[]{65}, result.get(0));
        assertArrayEquals(new int[]{66, 67}, result.get(1));
        assertArrayEquals(new int[]{68}, result.get(2));
    }

    @Test
    void concatenatesAdjacentTextBeforeTokenization() {
        var builder = new BPETrainingDataBuilder(
                new ByteLevelBPE(
                        new BpeVocabulary(
                                List.of(new BpeMergeRule(97, 98))
                        ).getMergeRules()
                )
        );

        List<int[]> result = builder.buildFromRecords(
                List.of(
                        new TrainingData.Document(
                                true,
                                List.of(
                                        text("a", null),
                                        text("b", null)
                                )
                        )
                )
        );

        assertEquals(1, result.size());

        assertArrayEquals(
                new int[]{274},
                result.getFirst()
        );
    }

    @Test
    void encodesLineBreakAsSpecialToken() {
        var builder = createBuilder();

        List<int[]> result = builder.buildFromRecords(
                List.of(
                        new TrainingData.Document(
                                true,
                                List.of(
                                        text("a", null),
                                        new TrainingData.Content(
                                                "line_break",
                                                null,
                                                null
                                        ),
                                        text("b", null)
                                )
                        )
                )
        );

        assertEquals(1, result.size());

        assertArrayEquals(
                new int[]{97, 258, 98},
                result.getFirst()
        );
    }

    @Test
    void inheritsConversationLossFromTurn() {
        var builder = createBuilder();

        TrainingData.Conversation conversation =
                new TrainingData.Conversation(
                        List.of(
                                new TrainingData.Turn(
                                        "user",
                                        false,
                                        List.of(
                                                new TrainingData.Event(
                                                        "message",
                                                        null,
                                                        List.of(
                                                                text("A", null),
                                                                text("B", true)
                                                        )
                                                ),
                                                new TrainingData.Event(
                                                        "search",
                                                        true,
                                                        List.of(text("C", null))
                                                )
                                        )
                                )
                        )
                );

        List<int[]> result = builder.buildFromRecords(
                List.of(conversation)
        );

        assertEquals(3, result.size());

        assertArrayEquals(new int[]{65}, result.get(0));
        assertArrayEquals(new int[]{66}, result.get(1));
        assertArrayEquals(new int[]{67}, result.get(2));
    }

    @Test
    void separatesDifferentEvents() {
        var builder = createBuilder();

        TrainingData.Conversation conversation =
                new TrainingData.Conversation(
                        List.of(
                                new TrainingData.Turn(
                                        "assistant",
                                        true,
                                        List.of(
                                                new TrainingData.Event(
                                                        "message",
                                                        null,
                                                        List.of(text("A", null))
                                                ),
                                                new TrainingData.Event(
                                                        "console",
                                                        null,
                                                        List.of(text("B", null))
                                                )
                                        )
                                )
                        )
                );

        List<int[]> result = builder.buildFromRecords(
                List.of(conversation)
        );

        assertEquals(2, result.size());

        assertArrayEquals(new int[]{65}, result.get(0));
        assertArrayEquals(new int[]{66}, result.get(1));
    }

    @Test
    void supportsMultipleRecords() {
        var builder = createBuilder();

        List<int[]> result = builder.buildFromRecords(
                List.of(
                        new TrainingData.Document(
                                true,
                                List.of(text("A", null))
                        ),
                        new TrainingData.Document(
                                true,
                                List.of(text("B", null))
                        )
                )
        );

        assertEquals(2, result.size());

        assertArrayEquals(new int[]{65}, result.get(0));
        assertArrayEquals(new int[]{66}, result.get(1));
    }

    @Test
    void ignoresEmptyText() {
        var builder = createBuilder();

        List<int[]> result = builder.buildFromRecords(
                List.of(
                        new TrainingData.Document(
                                true,
                                List.of(
                                        text("", null),
                                        text("A", null),
                                        text("", null)
                                )
                        )
                )
        );

        assertEquals(1, result.size());
        assertArrayEquals(new int[]{65}, result.getFirst());
    }

    @Test
    void treatsMediaContentAsSequenceBoundary() {
        var builder = createBuilder();

        List<int[]> result = builder.buildFromRecords(
                List.of(
                        new TrainingData.Document(
                                true,
                                List.of(
                                        text("A", null),
                                        new TrainingData.Content(
                                                "image",
                                                "image-data",
                                                null
                                        ),
                                        text("B", null)
                                )
                        )
                )
        );

        assertEquals(2, result.size());

        assertArrayEquals(new int[]{65}, result.get(0));
        assertArrayEquals(new int[]{66}, result.get(1));
    }

    @Test
    void supportsEmptyRecords() {
        var builder = createBuilder();

        List<int[]> result = builder.buildFromRecords(
                List.of(
                        new TrainingData.Document(
                                true,
                                List.of()
                        ),
                        new TrainingData.Conversation(
                                List.of()
                        )
                )
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsUnsupportedContentType() {
        var builder = createBuilder();

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.buildFromRecords(
                        List.of(
                                new TrainingData.Document(
                                        true,
                                        List.of(
                                                new TrainingData.Content(
                                                        "unknown",
                                                        "test",
                                                        null
                                                )
                                        )
                                )
                        )
                )
        );
    }

    @Test
    void rejectsNullTextValue() {
        var builder = createBuilder();

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.buildFromRecords(
                        List.of(
                                new TrainingData.Document(
                                        true,
                                        List.of(
                                                new TrainingData.Content(
                                                        "text",
                                                        null,
                                                        null
                                                )
                                        )
                                )
                        )
                )
        );
    }

    @Test
    void rejectsNullRecords() {
        var builder = createBuilder();

        assertThrows(
                NullPointerException.class,
                () -> builder.buildFromRecords(
                        java.util.Arrays.asList((TrainingData) null)
                )
        );
    }
}