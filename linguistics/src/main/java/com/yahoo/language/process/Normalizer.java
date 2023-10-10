// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

/**
 * <p>This interface provides NFKC normalization of Strings through the underlying linguistics library.</p>
 *
 * @author Mathias Mølster Lidal
 */
public interface Normalizer {

    /**
     * NFKC normalizes a String.
     *
     * @param input the string to normalize
     * @return the normalized string
     * @throws ProcessingException if underlying library throws an Exception
     */
    String normalize(String input);

}
