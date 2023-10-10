// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.sentencepiece;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple trie for sentencepiece token lookups.
 *
 * @author bratseth
 */
class Trie {

    final Node root = new Node();

    void add(TokenType type, int id, String word, float score) {
        Node current = root;
        for (char l : word.toCharArray())
            current = current.children.computeIfAbsent(l, c -> new Node());
        current.type = type;
        current.id = id;
        current.score = score;
    }

    static class Node {

        Integer id;
        TokenType type;
        Float score;
        final Map<Character, Node> children = new HashMap<>();

        boolean isToken() { return type != null; }

    }

}
