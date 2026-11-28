
package com.mc1510ty.TekitouAIStudio.transformer.tokenizer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class ByteLevelBPE implements Tokenizer {

    private final BpeVocabulary vocabulary;

    public ByteLevelBPE() {
        this(List.of());
    }

    public ByteLevelBPE(List<BpeMergeRule> mergeRules) {
        this(new BpeVocabulary(mergeRules));
    }

    private ByteLevelBPE(BpeVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    /**
     * 通常テキストをToken ID列に変換する。
     */
    @Override
    public int[] encodeText(String text) {

        Objects.requireNonNull(text, "text");

        byte[] bytes = encodeUtf8(text);

        return BpeEncoder.encode(bytes, vocabulary);
    }

    /**
     * Token ID列をテキストに復元する。
     */
    @Override
    public String decodeText(int[] tokenIds) {

        Objects.requireNonNull(tokenIds, "tokenIds");

        int totalLength = 0;

        for (int tokenId : tokenIds) {

            totalLength = Math.addExact(
                    totalLength,
                    vocabulary.getTokenByteLength(tokenId)
            );
        }

        byte[] bytes = new byte[totalLength];

        int offset = 0;

        for (int tokenId : tokenIds) {

            vocabulary.copyTokenBytesTo(
                    tokenId,
                    bytes,
                    offset
            );

            offset += vocabulary.getTokenByteLength(tokenId);
        }

        return decodeUtf8(bytes);
    }

    /**
     * StringをUTF-8バイト列へ変換する。
     */
    private static byte[] encodeUtf8(String text) {

        CharsetEncoder encoder =
                StandardCharsets.UTF_8.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {

            ByteBuffer buffer = encoder.encode(
                    CharBuffer.wrap(text)
            );

            byte[] bytes = new byte[buffer.remaining()];

            buffer.get(bytes);

            return bytes;

        } catch (CharacterCodingException e) {

            throw new IllegalArgumentException(
                    "不正なUnicode文字が含まれています",
                    e
            );
        }
    }

    /**
     * UTF-8バイト列をStringへ変換する。
     */
    private static String decodeUtf8(byte[] bytes) {

        CharsetDecoder decoder =
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {

            return decoder.decode(
                    ByteBuffer.wrap(bytes)
            ).toString();

        } catch (CharacterCodingException e) {

            throw new IllegalArgumentException(
                    "Token ID列が有効なUTF-8ではありません",
                    e
            );
        }
    }

    /**
     * 特殊トークンを含む総語彙数。
     */
    @Override
    public int vocabSize() {
        return vocabulary.vocabSize();
    }

    /**
     * Mergeルール一覧。
     */
    public List<BpeMergeRule> getMergeRules() {
        return vocabulary.getMergeRules();
    }

    /**
     * 語彙を拡張した新しいTokenizerを生成する。
     */
    public ByteLevelBPE withAdditionalMerges(
            List<BpeMergeRule> additionalMerges
    ) {

        BpeVocabulary expanded =
                vocabulary.withAdditionalMerges(additionalMerges);

        if (expanded == vocabulary) {
            return this;
        }

        return new ByteLevelBPE(expanded);
    }
}
