// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance;

/**
 *
 * @author MariusArhaug
 */
public record DocumentFrequency(long frequency, long corpusSize) {

    public DocumentFrequency(long frequency, long corpusSize) {
        this.frequency = frequency;
        this.corpusSize = corpusSize;
    }
}
