// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class SegmenterImpl implements Segmenter {

    private final Tokenizer tokenizer;

    public SegmenterImpl(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public List<String> segment(String input, Language language) {
        List<String> segments = new ArrayList<>();
        for (Token token : tokenizer.tokenize(input, language, StemMode.NONE, false)) {
            findSegments(token, segments);
        }
        if (segments.isEmpty()) {
            segments.add(input); // no segments, return original string
        }
        return segments;
    }

    private void findSegments(Token token, List<String> out) {
        int len;
        if (token.isSpecialToken() || (len = token.getNumComponents()) == 0) {
            if (token.isIndexable()) {
                String orig = token.getOrig();
                if (! orig.isEmpty()) {
                    out.add(orig);
                }
            }
        } else {
            for (int i = 0; i < len; ++i) {
                findSegments(token.getComponent(i), out);
            }
        }
    }

}
