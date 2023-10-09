// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;

import java.util.List;

/**
 * Interface providing stemming of single words.
 *
 * @author Mathias Mølster Lidal
 */
public interface Stemmer {

    /**
     * Stem input according to specified stemming mode.
     *
     * @param input    the string to stem.
     * @param mode     the stemming mode
     * @param language the language to use for stemming
     * @return a list of possible stems. Empty if none.
     * @throws ProcessingException thrown if there is an exception stemming this input
     */
    List<StemList> stem(String input, StemMode mode, Language language);

}
