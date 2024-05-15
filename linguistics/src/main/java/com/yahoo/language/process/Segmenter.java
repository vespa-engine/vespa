// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;

import java.util.List;

/**
 * A segmenter splits a string into separate segments (such as words) without applying any further
 * processing (such as stemming) on each segment.
 *
 * This is useful when token processing should be done separately from segmentation, such as in
 * linguistic processing of queries, where token processing depends on field settings in a specific
 * schema, while segmentation only depends on language and happens before schema-specific processing.
 *
 * @author Mathias Mølster Lidal
 */
public interface Segmenter {

    /**
     * Returns a list of segments produced from a string.
     *
     * @param input the text to segment
     * @param language the language of the input text
     * @return the resulting list of segments
     * @throws ProcessingException if an exception is encountered during processing
     */
    List<String> segment(String input, Language language);

}
