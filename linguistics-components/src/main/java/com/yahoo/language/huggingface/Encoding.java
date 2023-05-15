// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.huggingface;

import com.yahoo.api.annotations.Beta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author bjorncs
 */
@Beta
public record Encoding(
        List<Long> ids, List<Long> typeIds, List<String> tokens, List<Long> wordIds, List<Long> attentionMask,
        List<Long> specialTokenMask, List<CharSpan> charTokenSpans, List<Encoding> overflowing) {

    public record CharSpan(int start, int end) {
        public static final CharSpan NONE = new CharSpan(-1, -1);
        static CharSpan from(ai.djl.huggingface.tokenizers.jni.CharSpan s) {
            if (s == null) return NONE;
            return new CharSpan(s.getStart(), s.getEnd());
        }
        public boolean isNone() { return this.equals(NONE); }
    }

    public Encoding {
        ids = List.copyOf(ids);
        typeIds = List.copyOf(typeIds);
        tokens = List.copyOf(tokens);
        wordIds = List.copyOf(wordIds);
        attentionMask = List.copyOf(attentionMask);
        specialTokenMask = List.copyOf(specialTokenMask);
        charTokenSpans = List.copyOf(charTokenSpans);
        overflowing = List.copyOf(overflowing);
    }

    static Encoding from(ai.djl.huggingface.tokenizers.Encoding e) {
        return new Encoding(
                toList(e.getIds()),
                toList(e.getTypeIds()),
                List.of(e.getTokens()),
                toList(e.getWordIds()),
                toList(e.getAttentionMask()),
                toList(e.getSpecialTokenMask()),
                Arrays.stream(e.getCharTokenSpans()).map(CharSpan::from).toList(),
                Arrays.stream(e.getOverflowing()).map(Encoding::from).toList());
    }

    private static List<Long> toList(long[] array) {
        if (array == null) return List.of();
        var list = new ArrayList<Long>(array.length);
        for (long e : array) list.add(e);
        return list;
    }
}
