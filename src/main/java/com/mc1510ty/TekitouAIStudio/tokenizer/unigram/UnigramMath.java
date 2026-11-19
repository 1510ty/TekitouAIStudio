package com.mc1510ty.TekitouAIStudio.tokenizer.unigram;

import java.util.ArrayList;
import java.util.List;

final class UnigramMath {
    private UnigramMath() {
    }

    static UnigramTrie buildTrie(byte[][] pieceBytes) {
        UnigramTrie trie = new UnigramTrie();
        for (int i = 0; i < pieceBytes.length; i++) {
            UnigramTrie.Node node = trie.root;
            byte[] bytes = pieceBytes[i];
            for (byte value : bytes) {
                int key = value & 0xFF;
                node = node.children.computeIfAbsent(key, ignored -> new UnigramTrie.Node());
            }
            node.tokenId = Unigram.FIRST_PIECE_ID + i;
        }
        return trie;
    }

    static UnigramTrie buildTrie(List<UnigramCandidate> candidates) {
        byte[][] bytes = new byte[candidates.size()][];
        for (int i = 0; i < candidates.size(); i++) {
            bytes[i] = candidates.get(i).bytes;
        }
        return buildTrie(bytes);
    }

    @SuppressWarnings("unchecked")
    static List<UnigramEdge>[] buildLattice(byte[] input, UnigramTrie trie, double[] scores) {
        List<UnigramEdge>[] edges = (List<UnigramEdge>[]) new List<?>[input.length + 1];
        for (int i = 0; i < input.length; i++) {
            edges[i] = new ArrayList<>();
            edges[i].add(new UnigramEdge(i + 1, input[i] & 0xFF));

            UnigramTrie.Node node = trie.root;
            for (int j = i; j < input.length; j++) {
                node = node.children.get(input[j] & 0xFF);
                if (node == null) {
                    break;
                }
                if (node.tokenId >= 0 && node.tokenId < scores.length) {
                    edges[i].add(new UnigramEdge(j + 1, node.tokenId));
                }
            }
        }
        edges[input.length] = List.of();
        return edges;
    }

    /** Array-backed equivalent to buildLattice, avoiding a List object per byte position. */
    static UnigramEdge[][] buildLatticeArray(byte[] input, UnigramTrie trie, double[] scores) {
        UnigramEdge[][] edges = new UnigramEdge[input.length + 1][];
        for (int i = 0; i < input.length; i++) {
            List<UnigramEdge> atPosition = new ArrayList<>();
            atPosition.add(new UnigramEdge(i + 1, input[i] & 0xFF));

            UnigramTrie.Node node = trie.root;
            for (int j = i; j < input.length; j++) {
                node = node.children.get(input[j] & 0xFF);
                if (node == null) {
                    break;
                }
                if (node.tokenId >= 0 && node.tokenId < scores.length) {
                    atPosition.add(new UnigramEdge(j + 1, node.tokenId));
                }
            }
            edges[i] = atPosition.toArray(UnigramEdge[]::new);
        }
        edges[input.length] = new UnigramEdge[0];
        return edges;
    }

    static double logAdd(double a, double b) {
        if (a == Double.NEGATIVE_INFINITY) {
            return b;
        }
        if (b == Double.NEGATIVE_INFINITY) {
            return a;
        }
        double max = Math.max(a, b);
        return max + Math.log1p(Math.exp(Math.min(a, b) - max));
    }

    static int[] reconstruct(int[] previousPosition, int[] previousToken, int length) {
        List<Integer> reversed = new ArrayList<>();
        int position = length;
        while (position > 0) {
            int token = previousToken[position];
            if (token < 0) {
                throw new IllegalStateException("Could not reconstruct token path");
            }
            reversed.add(token);
            position = previousPosition[position];
        }

        int[] result = new int[reversed.size()];
        for (int i = 0; i < reversed.size(); i++) {
            result[i] = reversed.get(reversed.size() - 1 - i);
        }
        return result;
    }
}
