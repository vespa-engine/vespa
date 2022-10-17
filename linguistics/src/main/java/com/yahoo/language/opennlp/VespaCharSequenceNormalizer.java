// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import opennlp.tools.util.normalizer.CharSequenceNormalizer;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Simple normalizer
 *
 * @author arnej
 */
public class VespaCharSequenceNormalizer implements CharSequenceNormalizer {

    private static final VespaCharSequenceNormalizer INSTANCE = new VespaCharSequenceNormalizer();

    public static VespaCharSequenceNormalizer getInstance() {
        return INSTANCE;
    }

    // filter replacing sequences of non-letters with a single space
    static class OnlyLetters implements IntStream.IntMapMultiConsumer {
        boolean addSpace = false;
        public void accept(int codepoint, IntConsumer target) {
            if (WordCharDetector.isWordChar(codepoint)) {
                if (addSpace) {
                    target.accept(' ');
                    addSpace = false;
                }
                target.accept(Character.toLowerCase(codepoint));
            } else {
                addSpace = true;
            }
        }
    }

    public CharSequence normalize(CharSequence text) {
        if (text.isEmpty()) {
            return text;
        }
        var r = text
                .codePoints()
                .mapMulti(new OnlyLetters())
                .collect(StringBuilder::new,
                         StringBuilder::appendCodePoint,
                         StringBuilder::append);
        return r;
    }

}
