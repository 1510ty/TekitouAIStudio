package com.mc1510ty.TekitouAIStudio.transformer.tokenizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ByteLevelBPEIOTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsEmptyVocabulary() throws IOException {

        ByteLevelBPE original = new ByteLevelBPE();

        Path file = tempDir.resolve("empty.tok");

        ByteLevelBPEIO.save(original, file);

        ByteLevelBPE loaded = ByteLevelBPEIO.load(file);

        assertEquals(274, loaded.vocabSize());
        assertTrue(loaded.getMergeRules().isEmpty());
    }

    @Test
    void savesAndLoadsMergeRules() throws IOException {

        ByteLevelBPE original = new ByteLevelBPE(
                List.of(
                        new BpeMergeRule(97, 98),
                        new BpeMergeRule(274, 99)
                )
        );

        Path file = tempDir.resolve("model.tok");

        ByteLevelBPEIO.save(original, file);

        ByteLevelBPE loaded = ByteLevelBPEIO.load(file);

        assertEquals(
                original.getMergeRules(),
                loaded.getMergeRules()
        );

        assertEquals(
                original.vocabSize(),
                loaded.vocabSize()
        );
    }

    @Test
    void preservesEncodingAfterLoading() throws IOException {

        ByteLevelBPE original = new ByteLevelBPE(
                List.of(
                        new BpeMergeRule(97, 98),
                        new BpeMergeRule(274, 99)
                )
        );

        Path file = tempDir.resolve("encoding.tok");

        ByteLevelBPEIO.save(original, file);

        ByteLevelBPE loaded = ByteLevelBPEIO.load(file);

        assertArrayEquals(
                original.encodeText("abc"),
                loaded.encodeText("abc")
        );

        assertEquals(
                original.decodeText(original.encodeText("abc")),
                loaded.decodeText(loaded.encodeText("abc"))
        );
    }

    @Test
    void preservesJapaneseEncoding() throws IOException {

        ByteLevelBPE original = new ByteLevelBPE(
                List.of(
                        new BpeMergeRule(227, 129)
                )
        );

        Path file = tempDir.resolve("japanese.tok");

        ByteLevelBPEIO.save(original, file);

        ByteLevelBPE loaded = ByteLevelBPEIO.load(file);

        String text = "こんにちは、TekitouAIStudio！";

        assertArrayEquals(
                original.encodeText(text),
                loaded.encodeText(text)
        );
    }

    @Test
    void createsParentDirectories() throws IOException {

        ByteLevelBPE original = new ByteLevelBPE();

        Path file = tempDir.resolve(
                "nested/directory/tokenizer.tok"
        );

        ByteLevelBPEIO.save(original, file);

        assertTrue(Files.exists(file));

        ByteLevelBPE loaded = ByteLevelBPEIO.load(file);

        assertEquals(274, loaded.vocabSize());
    }

    @Test
    void replacesExistingFile() throws IOException {

        Path file = tempDir.resolve("replace.tok");

        ByteLevelBPEIO.save(
                new ByteLevelBPE(),
                file
        );

        ByteLevelBPE original = new ByteLevelBPE(
                List.of(new BpeMergeRule(97, 98))
        );

        ByteLevelBPEIO.save(original, file);

        ByteLevelBPE loaded = ByteLevelBPEIO.load(file);

        assertEquals(275, loaded.vocabSize());
        assertEquals(
                original.getMergeRules(),
                loaded.getMergeRules()
        );
    }

    @Test
    void detectsInvalidMagic() throws IOException {

        Path file = tempDir.resolve("invalid-magic.tok");

        ByteLevelBPEIO.save(new ByteLevelBPE(), file);

        byte[] bytes = Files.readAllBytes(file);

        bytes[0] ^= 0x01;

        Files.write(file, bytes);

        assertThrows(
                IOException.class,
                () -> ByteLevelBPEIO.load(file)
        );
    }

    @Test
    void detectsChecksumMismatch() throws IOException {

        Path file = tempDir.resolve("corrupted.tok");

        ByteLevelBPE original = new ByteLevelBPE(
                List.of(new BpeMergeRule(97, 98))
        );

        ByteLevelBPEIO.save(original, file);

        byte[] bytes = Files.readAllBytes(file);

        // Headerの直後にあるMerge Ruleを変更
        int headerSize = 108;

        bytes[headerSize] ^= 0x01;

        Files.write(file, bytes);

        assertThrows(
                IOException.class,
                () -> ByteLevelBPEIO.load(file)
        );
    }

    @Test
    void rejectsTruncatedFile() throws IOException {

        Path file = tempDir.resolve("truncated.tok");

        ByteLevelBPEIO.save(new ByteLevelBPE(), file);

        byte[] bytes = Files.readAllBytes(file);

        Files.write(
                file,
                java.util.Arrays.copyOf(
                        bytes,
                        bytes.length - 2
                )
        );

        assertThrows(
                IOException.class,
                () -> ByteLevelBPEIO.load(file)
        );
    }

    @Test
    void rejectsTrailingData() throws IOException {

        Path file = tempDir.resolve("trailing.tok");

        ByteLevelBPEIO.save(new ByteLevelBPE(), file);

        byte[] bytes = Files.readAllBytes(file);

        byte[] expanded = java.util.Arrays.copyOf(
                bytes,
                bytes.length + 1
        );

        expanded[expanded.length - 1] = 1;

        Files.write(file, expanded);

        assertThrows(
                IOException.class,
                () -> ByteLevelBPEIO.load(file)
        );
    }

    @Test
    void rejectsUnsupportedVersion() throws IOException {

        Path file = tempDir.resolve("version.tok");

        ByteLevelBPEIO.save(new ByteLevelBPE(), file);

        byte[] bytes = Files.readAllBytes(file);

        // VersionはMagicの次の4バイト
        bytes[7] = 2;

        Files.write(file, bytes);

        assertThrows(
                IOException.class,
                () -> ByteLevelBPEIO.load(file)
        );
    }

    @Test
    void rejectsDirectoryAsSource() {

        assertThrows(
                IOException.class,
                () -> ByteLevelBPEIO.load(tempDir)
        );
    }

    @Test
    void rejectsNullTokenizer() {

        assertThrows(
                NullPointerException.class,
                () -> ByteLevelBPEIO.save(
                        null,
                        tempDir.resolve("null.tok")
                )
        );
    }

    @Test
    void rejectsNullPath() {

        assertThrows(
                NullPointerException.class,
                () -> ByteLevelBPEIO.load(null)
        );
    }
}