// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import opennlp.tools.ngram.NGramCharModel;
import opennlp.tools.util.normalizer.CharSequenceNormalizer;

import java.util.HashSet;
import java.util.Set;

/**
 * Avoids using the unnecessarily slow {@link NGramCharModel}.
 *
 * @author jonmv
 */
public class DefaultLanguageDetectorContextGenerator extends opennlp.tools.langdetect.DefaultLanguageDetectorContextGenerator {

    public DefaultLanguageDetectorContextGenerator(int minLength, int maxLength, CharSequenceNormalizer... normalizers) {
        super(minLength, maxLength, normalizers);
    }

    @Override
    public String[] getContext(CharSequence document) {
        int[] normalized = normalizer.normalize(document).codePoints().map(Character::toLowerCase).toArray();
        Set<String> grams = new HashSet<>();
        for (int i = 0; i < normalized.length; i++)
            for (int j = minLength; j <= maxLength && i + j < normalized.length; j++)
                grams.add(new String(normalized, i, j));

        return grams.toArray(new String[grams.size()]);
    }

}
