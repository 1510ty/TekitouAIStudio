package com.mc1510ty.TekitouAIStudio.tokenizer.unigram;

final class UnigramEdge {
    final int end;
    final int tokenId;

    UnigramEdge(int end, int tokenId) {
        this.end = end;
        this.tokenId = tokenId;
    }
}
