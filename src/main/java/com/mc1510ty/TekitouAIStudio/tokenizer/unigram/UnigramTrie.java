package com.mc1510ty.TekitouAIStudio.tokenizer.unigram;

import java.util.HashMap;
import java.util.Map;

final class UnigramTrie {
    final Node root = new Node();

    static final class Node {
        final Map<Integer, Node> children = new HashMap<>();
        int tokenId = -1;
    }
}
