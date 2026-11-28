
package com.mc1510ty.TekitouAIStudio.transformer.tokenizer;

import java.util.List;

public final class SpecialTokens {

    private SpecialTokens() {
    }

    // Byte Level BPE
    public static final int BYTE_TOKEN_COUNT = 256;

    // 特殊トークンのID範囲
    public static final int FIRST_SPECIAL_TOKEN_ID = 256;
    public static final int LAST_SPECIAL_TOKEN_ID = 273;
    public static final int SPECIAL_TOKEN_COUNT = 18;

    // 通常のBPEトークン開始ID
    public static final int FIRST_BPE_TOKEN_ID = 274;

    // 特殊トークンID
    public static final int TURN_ASSISTANT_END_ID = 256;
    public static final int MESSAGE_END_ID = 257;
    public static final int LINE_BREAK_ID = 258;
    public static final int TURN_USER_START_ID = 259;
    public static final int TURN_USER_END_ID = 260;
    public static final int TURN_ASSISTANT_START_ID = 261;
    public static final int TURN_CONSOLE_START_ID = 262;
    public static final int TURN_CONSOLE_END_ID = 263;
    public static final int MESSAGE_START_ID = 264;
    public static final int DRAFT_START_ID = 265;
    public static final int DRAFT_END_ID = 266;
    public static final int DRAFT_PUSH_ID = 267;
    public static final int SEARCH_START_ID = 268;
    public static final int SEARCH_END_ID = 269;
    public static final int CONSOLE_START_ID = 270;
    public static final int CONSOLE_END_ID = 271;
    public static final int TURN_SEARCH_START_ID = 272;
    public static final int TURN_SEARCH_END_ID = 273;

    /**
     * 全特殊トークンIDの一覧。
     */
    private static final List<Integer> ALL_IDS =
            List.of(
                    TURN_ASSISTANT_END_ID,
                    MESSAGE_END_ID,
                    LINE_BREAK_ID,
                    TURN_USER_START_ID,
                    TURN_USER_END_ID,
                    TURN_ASSISTANT_START_ID,
                    TURN_CONSOLE_START_ID,
                    TURN_CONSOLE_END_ID,
                    MESSAGE_START_ID,
                    DRAFT_START_ID,
                    DRAFT_END_ID,
                    DRAFT_PUSH_ID,
                    SEARCH_START_ID,
                    SEARCH_END_ID,
                    CONSOLE_START_ID,
                    CONSOLE_END_ID,
                    TURN_SEARCH_START_ID,
                    TURN_SEARCH_END_ID
            );

    /**
     * 指定されたIDが特殊トークンか判定する。
     */
    public static boolean isSpecialToken(int id) {
        return id >= FIRST_SPECIAL_TOKEN_ID
                && id <= LAST_SPECIAL_TOKEN_ID;
    }

    /**
     * 全特殊トークンIDを取得する。
     */
    public static List<Integer> getAllIds() {
        return ALL_IDS;
    }
}
