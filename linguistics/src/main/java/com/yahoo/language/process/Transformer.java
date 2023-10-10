// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;

/**
 * Interface for providers of text transformations such as accent removal.
 *
 * @author Mathias Mølster Lidal
 */
public interface Transformer {

    /**
     * Remove accents from input text.
     *
     * @param input    text to transform
     * @param language language of input text
     * @return text with accents removed, or input-text if the feature is unavailable
     * @throws ProcessingException thrown if there is an exception stemming this input
     */
    String accentDrop(String input, Language language);

}
