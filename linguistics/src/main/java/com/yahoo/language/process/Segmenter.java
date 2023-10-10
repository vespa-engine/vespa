// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;

import java.util.List;

/**
 * Interface providing segmentation, i.e. splitting of CJK character blocks into separate tokens. This is primarily a
 * convenience feature for users who don't need full tokenization (or who use a separate tokenizer and only need CJK
 * processing).
 *
 * @author Mathias Mølster Lidal
 */
public interface Segmenter {

    /**
     * Split input-string into tokens, and returned a list of tokens in unprocessed form (i.e. lowercased, normalized
     * and stemmed if applicable, see @link{StemMode} for list of stemming options). It is assumed that the input only
     * contains word-characters, any punctuation and spacing tokens will be removed.
     *
     * @param input the text to segment.
     * @param language language of input text.
     * @return the list of segments.
     * @throws ProcessingException if an exception is encountered during processing
     */
    List<String> segment(String input, Language language);

}
