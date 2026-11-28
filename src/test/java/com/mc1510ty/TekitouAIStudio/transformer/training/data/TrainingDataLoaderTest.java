
package com.mc1510ty.TekitouAIStudio.transformer.training.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrainingDataLoaderTest {

    @TempDir
    Path tempDir;

    /**
     * テスト用JSONLファイルを作成する。
     */
    private Path createJsonl(String... lines)
            throws IOException {

        Path path = tempDir.resolve("training.jsonl");

        List<String> normalized =
                java.util.Arrays.stream(lines)
                        .map(String::strip)
                        .toList();

        Files.write(
                path,
                normalized,
                StandardCharsets.UTF_8
        );

        return path;
    }

    /**
     * 事前学習用Documentの読み込みテスト。
     */
    @Test
    void testReadDocument() throws IOException {

        Path path = createJsonl(
                """
                {"record_type":"document","loss":true,"content":[{"type":"text","value":"Transformerはニューラルネットワークの構造である。"}]}
                """
        );

        try (TrainingDataLoader loader =
                     new TrainingDataLoader(path)) {

            TrainingData data = loader.readNext();

            assertNotNull(data);
            assertInstanceOf(TrainingData.Document.class, data);

            TrainingData.Document document =
                    (TrainingData.Document) data;

            assertTrue(document.loss());
            assertEquals(1, document.content().size());

            TrainingData.Content content =
                    document.content().getFirst();

            assertEquals("text", content.type());
            assertEquals(
                    "Transformerはニューラルネットワークの構造である。",
                    content.value()
            );

            assertNull(content.loss());
            assertNull(loader.readNext());
        }
    }

    /**
     * 対話学習用Conversationの読み込みテスト。
     */
    @Test
    void testReadConversation() throws IOException {

        Path path = createJsonl(
                """
                {"record_type":"conversation","turns":[{"role":"user","loss":false,"events":[{"type":"message","content":[{"type":"text","value":"こんにちは"}]}]},{"role":"assistant","loss":true,"events":[{"type":"message","content":[{"type":"text","value":"こんにちは！"},{"type":"line_break"},{"type":"text","value":"今日はどうしたの？"}]}]}]}
                """
        );

        try (TrainingDataLoader loader =
                     new TrainingDataLoader(path)) {

            TrainingData data = loader.readNext();

            assertNotNull(data);
            assertInstanceOf(
                    TrainingData.Conversation.class,
                    data
            );

            TrainingData.Conversation conversation =
                    (TrainingData.Conversation) data;

            assertEquals(2, conversation.turns().size());

            // User Turn
            TrainingData.Turn user =
                    conversation.turns().get(0);

            assertEquals("user", user.role());
            assertFalse(user.loss());

            // Assistant Turn
            TrainingData.Turn assistant =
                    conversation.turns().get(1);

            assertEquals("assistant", assistant.role());
            assertTrue(assistant.loss());

            TrainingData.Event event =
                    assistant.events().getFirst();

            assertEquals("message", event.type());

            assertEquals(3, event.content().size());

            assertEquals(
                    "こんにちは！",
                    event.content().get(0).value()
            );

            assertEquals(
                    "line_break",
                    event.content().get(1).type()
            );

            assertNull(event.content().get(1).value());

            assertEquals(
                    "今日はどうしたの？",
                    event.content().get(2).value()
            );
        }
    }

    /**
     * Lossの継承情報が保持されるか確認する。
     */
    @Test
    void testLossInheritanceData() throws IOException {

        Path path = createJsonl(
                """
                {"record_type":"conversation","turns":[{"role":"assistant","loss":true,"events":[{"type":"message","content":[{"type":"text","value":"対象"},{"type":"text","value":"除外","loss":false}]}]}]}
                """
        );

        try (TrainingDataLoader loader =
                     new TrainingDataLoader(path)) {

            TrainingData.Conversation conversation =
                    (TrainingData.Conversation) loader.readNext();

            TrainingData.Turn turn =
                    conversation.turns().getFirst();

            TrainingData.Event event =
                    turn.events().getFirst();

            assertTrue(turn.loss());

            assertNull(event.loss());

            assertNull(event.content().get(0).loss());

            assertFalse(event.content().get(1).loss());
        }
    }

    /**
     * 複数レコードの逐次読み込みテスト。
     */
    @Test
    void testReadMultipleRecords() throws IOException {

        Path path = createJsonl(
                """
                {"record_type":"document","loss":true,"content":[{"type":"text","value":"文章A"}]}
                """,
                "",
                """
                {"record_type":"document","loss":false,"content":[{"type":"text","value":"文章B"}]}
                """
        );

        try (TrainingDataLoader loader =
                     new TrainingDataLoader(path)) {

            TrainingData first = loader.readNext();
            TrainingData second = loader.readNext();
            TrainingData third = loader.readNext();

            assertNotNull(first);
            assertNotNull(second);
            assertNull(third);

            assertTrue(
                    ((TrainingData.Document) first).loss()
            );

            assertFalse(
                    ((TrainingData.Document) second).loss()
            );

            assertEquals(3, loader.getLineNumber());
        }
    }

    /**
     * 未対応のrecord_typeを検出するテスト。
     */
    @Test
    void testUnknownRecordType() throws IOException {

        Path path = createJsonl(
                """
                {"record_type":"unknown"}
                """
        );

        try (TrainingDataLoader loader =
                     new TrainingDataLoader(path)) {

            IOException exception = assertThrows(
                    IOException.class,
                    loader::readNext
            );

            assertTrue(
                    exception.getMessage().contains(
                            "未対応のrecord_type"
                    )
            );

            assertTrue(
                    exception.getMessage().contains("1行目")
            );
        }
    }

    /**
     * JSONとして不正なデータを検出するテスト。
     */
    @Test
    void testInvalidJson() throws IOException {

        Path path = createJsonl(
                """
                {"record_type":"document","loss":true,"content":[
                """
        );

        try (TrainingDataLoader loader =
                     new TrainingDataLoader(path)) {

            assertThrows(
                    IOException.class,
                    loader::readNext
            );
        }
    }

    /**
     * 1行に複数のJSONが存在する場合のテスト。
     */
    @Test
    void testMultipleJsonValues() throws IOException {

        Path path = createJsonl(
                """
                {"record_type":"document","loss":true,"content":[]} {"record_type":"document","loss":true,"content":[]}
                """
        );

        try (TrainingDataLoader loader =
                     new TrainingDataLoader(path)) {

            IOException exception = assertThrows(
                    IOException.class,
                    loader::readNext
            );

            assertTrue(
                    exception.getMessage().contains(
                            "複数のJSON値"
                    )
            );
        }
    }
}
