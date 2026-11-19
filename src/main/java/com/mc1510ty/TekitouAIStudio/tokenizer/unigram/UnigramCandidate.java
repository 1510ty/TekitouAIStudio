package com.mc1510ty.TekitouAIStudio.tokenizer.unigram;

final class UnigramCandidate {
    final String text;
    final byte[] bytes;
    final long frequency;

    UnigramCandidate(String text, byte[] bytes, long frequency) {
        this.text = text;
        this.bytes = bytes;
        this.frequency = frequency;
    }
}
