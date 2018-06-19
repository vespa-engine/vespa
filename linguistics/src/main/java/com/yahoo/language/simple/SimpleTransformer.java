// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.Language;
import com.yahoo.language.process.Transformer;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Converts all accented characters into their de-accented counterparts followed by their combining diacritics, then
 * strips off the diacritics using a regex.
 *
 * @author Simon Thoresen
 */
public class SimpleTransformer implements Transformer {

    private final static Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    @Override
    public String accentDrop(String input, Language language) {
        return pattern.matcher(Normalizer.normalize(input, Normalizer.Form.NFD)).replaceAll("");
    }

}
