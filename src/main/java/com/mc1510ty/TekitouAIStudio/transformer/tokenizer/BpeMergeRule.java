
package com.mc1510ty.TekitouAIStudio.transformer.tokenizer;

public record BpeMergeRule(
        int leftTokenId,
        int rightTokenId
) {

    public BpeMergeRule {
        if (leftTokenId < 0 || rightTokenId < 0) {
            throw new IllegalArgumentException(
                    "Token IDは0以上である必要があります"
            );
        }
    }
}
