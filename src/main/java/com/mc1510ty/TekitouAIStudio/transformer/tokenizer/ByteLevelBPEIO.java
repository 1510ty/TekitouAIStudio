package com.mc1510ty.TekitouAIStudio.transformer.tokenizer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;

public final class ByteLevelBPEIO {

    private ByteLevelBPEIO() {
    }

    // ファイル識別子 "TAIS"
    private static final int MAGIC = 0x54414953;

    // ファイル形式バージョン
    private static final int FORMAT_VERSION = 1;

    // Tokenizerの種類
    private static final int TOKENIZER_TYPE_BYTE_LEVEL_BPE = 1;

    // Headerの整数フィールド数（特殊トークン一覧を除く）
    private static final int HEADER_BASE_INT_COUNT = 9;

    private static final int HEADER_INT_COUNT =
            HEADER_BASE_INT_COUNT + SpecialTokens.SPECIAL_TOKEN_COUNT;

    private static final int HEADER_SIZE =
            HEADER_INT_COUNT * Integer.BYTES;

    private static final int CHECKSUM_SIZE = Integer.BYTES;

    private static final int FIXED_FILE_SIZE =
            HEADER_SIZE + CHECKSUM_SIZE;

    /**
     * ByteLevelBPEをバイナリファイルに保存する。
     *
     * 一時ファイルに書き込んだ後、保存先へ移動する。
     * 既存ファイルがあれば置き換える。
     */
    public static void save(
            ByteLevelBPE tokenizer,
            Path destination
    ) throws IOException {

        Objects.requireNonNull(tokenizer, "tokenizer");
        Objects.requireNonNull(destination, "destination");

        List<BpeMergeRule> mergeRules =
                tokenizer.getMergeRules();

        List<Integer> specialTokenIds =
                SpecialTokens.getAllIds();

        if (specialTokenIds.size()
                != SpecialTokens.SPECIAL_TOKEN_COUNT) {

            throw new IllegalStateException(
                    "Special token count does not match its definition"
            );
        }

        Path absolutePath =
                destination.toAbsolutePath().normalize();

        Path parent = absolutePath.getParent();

        if (parent == null || absolutePath.getFileName() == null) {
            throw new IOException(
                    "Invalid destination path: " + destination
            );
        }

        Files.createDirectories(parent);

        Path temporaryFile = Files.createTempFile(
                parent,
                ".taistok-",
                ".tmp"
        );

        boolean moved = false;

        try {
            writeFile(
                    temporaryFile,
                    mergeRules,
                    specialTokenIds
            );

            try {
                Files.move(
                        temporaryFile,
                        absolutePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(
                        temporaryFile,
                        absolutePath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            moved = true;

        } finally {
            if (!moved) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    /**
     * バイナリファイルからByteLevelBPEを復元する。
     */
    public static ByteLevelBPE load(Path source)
            throws IOException {

        Objects.requireNonNull(source, "source");

        if (!Files.isRegularFile(source)) {
            throw new IOException(
                    "Tokenizer file does not exist or is not a regular file: "
                            + source
            );
        }

        long fileSize = Files.size(source);

        if (fileSize < FIXED_FILE_SIZE) {
            throw new IOException(
                    "Tokenizer file is too small"
            );
        }

        CRC32 crc = new CRC32();

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(
                        Files.newInputStream(source)
                )
        )) {

            int magic = readCheckedInt(input, crc);

            if (magic != MAGIC) {
                throw new IOException(
                        "Invalid tokenizer file magic"
                );
            }

            int version = readCheckedInt(input, crc);

            if (version != FORMAT_VERSION) {
                throw new IOException(
                        "Unsupported tokenizer file version: "
                                + version
                );
            }

            int tokenizerType = readCheckedInt(input, crc);

            if (tokenizerType != TOKENIZER_TYPE_BYTE_LEVEL_BPE) {
                throw new IOException(
                        "Unsupported tokenizer type: "
                                + tokenizerType
                );
            }

            int mergeCount = validateHeader(input, crc);

            if (mergeCount < 0) {
                throw new IOException(
                        "Invalid merge rule count: " + mergeCount
                );
            }

            long expectedSize;

            try {
                expectedSize = Math.addExact(
                        FIXED_FILE_SIZE,
                        Math.multiplyExact(
                                (long) mergeCount,
                                2L * Integer.BYTES
                        )
                );

            } catch (ArithmeticException e) {
                throw new IOException(
                        "Tokenizer file size overflow",
                        e
                );
            }

            if (fileSize != expectedSize) {
                throw new IOException(
                        "Tokenizer file size does not match its header"
                );
            }

            List<BpeMergeRule> mergeRules =
                    new ArrayList<>(mergeCount);

            try {
                for (int i = 0; i < mergeCount; i++) {

                    int leftTokenId = readCheckedInt(input, crc);
                    int rightTokenId = readCheckedInt(input, crc);

                    mergeRules.add(
                            new BpeMergeRule(
                                    leftTokenId,
                                    rightTokenId
                            )
                    );
                }

            } catch (IllegalArgumentException e) {
                throw new IOException(
                        "Invalid BPE merge rule",
                        e
                );
            }

            // Checksum自体はCRC計算に含めない
            int storedChecksum = input.readInt();

            if (storedChecksum != (int) crc.getValue()) {
                throw new IOException(
                        "Tokenizer file checksum mismatch"
                );
            }

            if (input.read() != -1) {
                throw new IOException(
                        "Unexpected trailing data"
                );
            }

            try {
                return new ByteLevelBPE(mergeRules);

            } catch (IllegalArgumentException
                     | ArithmeticException e) {

                throw new IOException(
                        "Failed to reconstruct BPE vocabulary",
                        e
                );
            }
        }
    }

    /**
     * ファイルを書き込む。
     */
    private static void writeFile(
            Path path,
            List<BpeMergeRule> mergeRules,
            List<Integer> specialTokenIds
    ) throws IOException {

        CRC32 crc = new CRC32();

        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(
                        Files.newOutputStream(path)
                )
        )) {

            writeCheckedInt(output, crc, MAGIC);
            writeCheckedInt(output, crc, FORMAT_VERSION);

            writeCheckedInt(
                    output,
                    crc,
                    TOKENIZER_TYPE_BYTE_LEVEL_BPE
            );

            writeCheckedInt(
                    output,
                    crc,
                    SpecialTokens.BYTE_TOKEN_COUNT
            );

            writeCheckedInt(
                    output,
                    crc,
                    SpecialTokens.FIRST_SPECIAL_TOKEN_ID
            );

            writeCheckedInt(
                    output,
                    crc,
                    SpecialTokens.LAST_SPECIAL_TOKEN_ID
            );

            writeCheckedInt(
                    output,
                    crc,
                    SpecialTokens.FIRST_BPE_TOKEN_ID
            );

            writeCheckedInt(
                    output,
                    crc,
                    specialTokenIds.size()
            );

            writeCheckedInt(
                    output,
                    crc,
                    mergeRules.size()
            );

            // 特殊トークンIDの定義を保存
            for (int tokenId : specialTokenIds) {
                writeCheckedInt(output, crc, tokenId);
            }

            // Merge Rank順に保存
            for (BpeMergeRule rule : mergeRules) {

                writeCheckedInt(
                        output,
                        crc,
                        rule.leftTokenId()
                );

                writeCheckedInt(
                        output,
                        crc,
                        rule.rightTokenId()
                );
            }

            // Checksum
            output.writeInt((int) crc.getValue());
        }
    }

    /**
     * Tokenizerの基本設定が現在の実装と一致するか確認する。
     */

    private static int validateHeader(
            DataInputStream input,
            CRC32 crc
    ) throws IOException {

        int byteTokenCount = readCheckedInt(input, crc);

        int firstSpecialTokenId = readCheckedInt(input, crc);

        int lastSpecialTokenId = readCheckedInt(input, crc);

        int firstBpeTokenId = readCheckedInt(input, crc);

        int specialTokenCount = readCheckedInt(input, crc);

        // 保存時の順序に合わせて先に読み込む
        int mergeCount = readCheckedInt(input, crc);

        if (byteTokenCount != SpecialTokens.BYTE_TOKEN_COUNT
                || firstSpecialTokenId
                != SpecialTokens.FIRST_SPECIAL_TOKEN_ID
                || lastSpecialTokenId
                != SpecialTokens.LAST_SPECIAL_TOKEN_ID
                || firstBpeTokenId
                != SpecialTokens.FIRST_BPE_TOKEN_ID
                || specialTokenCount
                != SpecialTokens.SPECIAL_TOKEN_COUNT) {

            throw new IOException(
                    "Tokenizer special token configuration mismatch"
            );
        }

        if (mergeCount < 0) {
            throw new IOException(
                    "Invalid merge rule count: " + mergeCount
            );
        }

        List<Integer> expectedIds =
                SpecialTokens.getAllIds();

        for (int i = 0; i < specialTokenCount; i++) {

            int storedId = readCheckedInt(input, crc);

            if (storedId != expectedIds.get(i)) {
                throw new IOException(
                        "Special token ID mismatch at index: " + i
                );
            }
        }

        return mergeCount;
    }

    /**
     * 整数を書き込みながらCRC32を更新する。
     */
    private static void writeCheckedInt(
            DataOutputStream output,
            CRC32 crc,
            int value
    ) throws IOException {

        output.writeInt(value);
        updateInt(crc, value);
    }

    /**
     * 整数を読み込みながらCRC32を更新する。
     */
    private static int readCheckedInt(
            DataInputStream input,
            CRC32 crc
    ) throws IOException {

        int value = input.readInt();

        updateInt(crc, value);

        return value;
    }

    /**
     * 整数の4バイトをCRC32へ反映する。
     * DataInputStream / DataOutputStreamと同じビッグエンディアン。
     */
    private static void updateInt(CRC32 crc, int value) {

        crc.update((value >>> 24) & 0xFF);
        crc.update((value >>> 16) & 0xFF);
        crc.update((value >>> 8) & 0xFF);
        crc.update(value & 0xFF);
    }
}