// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/*
 * The expected number of documents matching the item given a corpus of
 * multiple documents. This is the raw data used to calculate variants
 * of idf, used as significance.
 */
public record DocumentFrequency(long frequency, long corpusSize) {
}
