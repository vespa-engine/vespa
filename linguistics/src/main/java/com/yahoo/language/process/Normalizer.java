// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

/**
 * <p>This interface provides NFKC normalization of Strings through the underlying linguistics library.</p>
 *
 * @author <a href="mailto:mathiasm@yahoo-inc.com">Mathias M\u00F8lster Lidal</a>
 */
public interface Normalizer {

    /**
     * <p>NFKC normalizes a String.</p>
     *
     * @param input String to normalize.
     * @return The normalized String.
     * @throws ProcessingException If underlying library throws an Exception.
     */
    public String normalize(String input);
}
