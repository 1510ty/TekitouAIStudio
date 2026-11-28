
package com.mc1510ty.TekitouAIStudio.transformer.tokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BpeVocabulary {

    private final List<BpeMergeRule> mergeRules;

    private final Map<Long, Integer> pairToRank;

    private final byte[][] tokenBytes;

    private final int vocabSize;

    public BpeVocabulary(List<BpeMergeRule> mergeRules) {

        Objects.requireNonNull(mergeRules, "mergeRules");

        this.mergeRules = List.copyOf(mergeRules);

        this.vocabSize = Math.addExact(
                SpecialTokens.FIRST_BPE_TOKEN_ID,
                this.mergeRules.size()
        );

        tokenBytes = new byte[vocabSize][];

        // 基本Byte Token
        for (int i = 0; i < SpecialTokens.BYTE_TOKEN_COUNT; i++) {
            tokenBytes[i] = new byte[]{(byte) i};
        }

        Map<Long, Integer> ranks = new HashMap<>();

        // Merge Rank順に語彙を構築
        for (int rank = 0; rank < this.mergeRules.size(); rank++) {

            BpeMergeRule rule = this.mergeRules.get(rank);

            int newTokenId =
                    SpecialTokens.FIRST_BPE_TOKEN_ID + rank;

            byte[] left = requireTokenBytes(
                    rule.leftTokenId(),
                    newTokenId
            );

            byte[] right = requireTokenBytes(
                    rule.rightTokenId(),
                    newTokenId
            );

            long key = pairKey(
                    rule.leftTokenId(),
                    rule.rightTokenId()
            );

            if (ranks.putIfAbsent(key, rank) != null) {
                throw new IllegalArgumentException(
                        "Mergeルールが重複しています: " + rank
                );
            }

            tokenBytes[newTokenId] = concatenate(left, right);
        }

        pairToRank = Map.copyOf(ranks);
    }

    /**
     * Merge元Tokenのバイト列を取得する。
     */
    private byte[] requireTokenBytes(
            int tokenId,
            int newTokenId
    ) {

        if (tokenId < 0 ||
                tokenId >= newTokenId ||
                tokenId >= tokenBytes.length ||
                tokenBytes[tokenId] == null) {

            throw new IllegalArgumentException(
                    "不正なMerge元Token ID: " + tokenId
            );
        }

        return tokenBytes[tokenId];
    }

    /**
     * Token IDペアをMap用のキーに変換する。
     */
    static long pairKey(int left, int right) {

        return ((long) left << 32)
                | (right & 0xFFFFFFFFL);
    }

    /**
     * バイト列を連結する。
     */
    private static byte[] concatenate(
            byte[] left,
            byte[] right
    ) {

        byte[] result = new byte[
                Math.addExact(left.length, right.length)
                ];

        System.arraycopy(
                left, 0,
                result, 0,
                left.length
        );

        System.arraycopy(
                right, 0,
                result, left.length,
                right.length
        );

        return result;
    }

    /**
     * Token IDペアからMerge Rankを取得する。
     * 存在しなければ-1。
     */
    int findMergeRank(int left, int right) {

        return pairToRank.getOrDefault(
                pairKey(left, right),
                -1
        );
    }

    /**
     * Token IDに対応するバイト数。
     */
    int getTokenByteLength(int tokenId) {

        validateTokenId(tokenId);

        return tokenBytes[tokenId].length;
    }

    /**
     * Token IDのバイト列を指定配列へコピーする。
     */
    void copyTokenBytesTo(
            int tokenId,
            byte[] destination,
            int offset
    ) {

        validateTokenId(tokenId);

        Objects.requireNonNull(destination, "destination");

        System.arraycopy(
                tokenBytes[tokenId],
                0,
                destination,
                offset,
                tokenBytes[tokenId].length
        );
    }

    /**
     * 通常Token IDの検証。
     */
    private void validateTokenId(int tokenId) {

        if (tokenId < 0 || tokenId >= vocabSize) {
            throw new IllegalArgumentException(
                    "Token IDが語彙範囲外です: " + tokenId
            );
        }

        if (SpecialTokens.isSpecialToken(tokenId)) {
            throw new IllegalArgumentException(
                    "特殊Token IDは使用できません: " + tokenId
            );
        }

        if (tokenBytes[tokenId] == null) {
            throw new IllegalArgumentException(
                    "無効なToken IDです: " + tokenId
            );
        }
    }

    public int vocabSize() {
        return vocabSize;
    }

    public List<BpeMergeRule> getMergeRules() {
        return mergeRules;
    }

    /**
     * 既存語彙を保持したまま新しいMergeを追加する。
     */
    public BpeVocabulary withAdditionalMerges(
            List<BpeMergeRule> additionalMerges
    ) {

        Objects.requireNonNull(
                additionalMerges,
                "additionalMerges"
        );

        if (additionalMerges.isEmpty()) {
            return this;
        }

        List<BpeMergeRule> expanded =
                new ArrayList<>(mergeRules);

        expanded.addAll(additionalMerges);

        return new BpeVocabulary(expanded);
    }
}
