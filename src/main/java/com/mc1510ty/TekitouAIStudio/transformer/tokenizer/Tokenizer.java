
package com.mc1510ty.TekitouAIStudio.transformer.tokenizer;

/**
 * Tokenizerの共通インターフェース。
 */
public interface Tokenizer {

    /**
     * 通常のテキストをToken ID列に変換する。
     *
     * 特殊トークンの文字列表現は認識せず、
     * すべて通常のテキストとして処理する。
     *
     * @param text 入力テキスト
     * @return Token ID配列
     */
    int[] encodeText(String text);

    /**
     * 通常のToken ID列をテキストに変換する。
     *
     * 特殊トークンIDは受け付けない。
     *
     * @param tokenIds Token ID配列
     * @return 復元されたテキスト
     */
    String decodeText(int[] tokenIds);

    /**
     * 特殊トークンを含めた総語彙数を取得する。
     *
     * @return Vocabulary Size
     */
    int vocabSize();

}
