
package com.mc1510ty.TekitouAIStudio.transformer.training.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TrainingDatasetTest {

    @TempDir
    Path tempDir;

    /**
     * 複数ファイルをファイル名順に読み込めるか。
     */
    @Test
    void testMultipleFiles() throws IOException {

        Files.writeString(
                tempDir.resolve("0002.zizenn.jsonl"),
                """
                {"record_type":"document","loss":true,"content":[{"type":"text","value":"文章B"}]}
                """,
                StandardCharsets.UTF_8
        );

        Files.writeString(
                tempDir.resolve("0001.zizenn.jsonl"),
                """
                {"record_type":"document","loss":true,"content":[{"type":"text","value":"文章A"}]}
                """,
                StandardCharsets.UTF_8
        );

        // 対象外のファイル
        Files.writeString(
                tempDir.resolve("0001.taiwa.jsonl"),
                "{}",
                StandardCharsets.UTF_8
        );

        try (TrainingDataset dataset =
                     new TrainingDataset(
                             tempDir,
                             TrainingDataset.Type.PRETRAINING
                     )) {

            assertEquals(2, dataset.getFileCount());

            TrainingData first = dataset.readNext();
            TrainingData second = dataset.readNext();
            TrainingData third = dataset.readNext();

            assertNotNull(first);
            assertNotNull(second);
            assertNull(third);

            TrainingData.Document documentA =
                    (TrainingData.Document) first;

            TrainingData.Document documentB =
                    (TrainingData.Document) second;

            assertEquals(
                    "文章A",
                    documentA.content().getFirst().value()
            );

            assertEquals(
                    "文章B",
                    documentB.content().getFirst().value()
            );

            assertEquals(2, dataset.getRecordsRead());
        }
    }

    /**
     * ファイル名とrecord_typeが一致しない場合。
     */
    @Test
    void testRecordTypeMismatch() throws IOException {

        Files.writeString(
                tempDir.resolve("0001.zizenn.jsonl"),
                """
                {"record_type":"conversation","turns":[]}
                """,
                StandardCharsets.UTF_8
        );

        try (TrainingDataset dataset =
                     new TrainingDataset(
                             tempDir,
                             TrainingDataset.Type.PRETRAINING
                     )) {

            assertThrows(
                    IOException.class,
                    dataset::readNext
            );
        }
    }

    /**
     * 対象ファイルがない場合。
     */
    @Test
    void testNoMatchingFiles() {

        assertThrows(
                IOException.class,
                () -> new TrainingDataset(
                        tempDir,
                        TrainingDataset.Type.PRETRAINING
                )
        );
    }
}
