// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.wordpiece;

import com.yahoo.collections.Tuple2;
import com.yahoo.language.Language;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.Tokenizer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A WordPiece embedder "model" - just a vocabulary of strings with a fixed id (index).
 *
 * Adapted from
 * https://github.com/eclipse/deeplearning4j/blob/master/deeplearning4j/deeplearning4j-nlp-parent/deeplearning4j-nlp/src/main/java/org/deeplearning4j/text/tokenization/tokenizer/BertWordPieceTokenizer.java
 * licensed under the Apache License, Version 2.0
 *
 * @author bergum
 * @author bratseth
 */
class Model {

    private final String subwordPrefix;
    private final Path source;
    private final Language language;
    private final NavigableMap<String, Integer> vocabulary;
    private final Map<Integer, String> tokenId2Token;

    Model(String subwordPrefix, Language language, Path path) {
        this.subwordPrefix = subwordPrefix;
        this.source = path;
        this.language = language;

        this.vocabulary = new TreeMap<>(Collections.reverseOrder());
        this.tokenId2Token = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path.toFile()),
                                                                              StandardCharsets.UTF_8))) {
            String token;
            int i = 0;
            while ((token = reader.readLine()) != null) {
                this.vocabulary.put(token, i);
                this.tokenId2Token.put(i, token);
                i++;
            }
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read a WordPiece model from " + path, e);
        }

    }

    Language language() { return language; }

    List<Integer> embed(String text, Tokenizer tokenizer) {
        List<Integer> ids = new ArrayList<>();
        text = text.toLowerCase();
        for (Token t : tokenizer.tokenize(text, language, StemMode.NONE, true)) {
            String originalToken = t.getTokenString();
            String candidate = originalToken;
            int count = 0;
            while (candidate.length() > 0 && !candidate.equals(subwordPrefix)) {
                Tuple2<String, Integer> entry = findLongestSubstring(candidate);
                if (entry == null) break;
                ids.add(entry.second);
                candidate = subwordPrefix + candidate.substring(entry.first.length());
                if (count++ > originalToken.length()) break;
            }
        }

        return ids;
    }

    List<String> segment(String text, Tokenizer tokenizer) {
        return embed(text, tokenizer).stream().map(tokenId -> tokenId2Token.get(tokenId)).collect(Collectors.toList());
    }

    private Tuple2<String, Integer> findLongestSubstring(String candidate) {
        NavigableMap<String, Integer> tailMap = this.vocabulary.tailMap(candidate, true);
        if (tailMap.isEmpty())
            return null;
        String longestSubstring = tailMap.firstKey();
        Integer id = tailMap.firstEntry().getValue();
        int subStringLength = Math.min(candidate.length(), longestSubstring.length());
        while (!candidate.startsWith(longestSubstring)) {
            subStringLength--;
            tailMap = tailMap.tailMap(candidate.substring(0, subStringLength), true);
            if (tailMap.isEmpty())
                return null;
            longestSubstring = tailMap.firstKey();
            id = tailMap.firstEntry().getValue();
        }
        return new Tuple2<>(longestSubstring, id);
    }

    @Override
    public String toString() {
        return "WordPiece model for " + language + ": '" + source + "'";
    }

}
