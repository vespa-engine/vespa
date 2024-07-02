// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.api.annotations.Beta;

/**
 * The expected number of documents matching an item given a corpus of
 * multiple documents. This is the raw data used to calculate variants
 * of idf, used as significance.
 *
 * @param frequency The number of documents in which an item occurs
 * @param count     The total number of documents in the corpus
 */
@Beta
public record DocumentFrequency(long frequency, long count) {
}
