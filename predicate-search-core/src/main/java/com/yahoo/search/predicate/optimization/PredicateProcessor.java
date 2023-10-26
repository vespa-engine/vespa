// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.optimization;

import com.yahoo.document.predicate.Predicate;

/**
 * A predicate processor takes a predicate, processes it and returns the result.
 * Predicate optimisers typically implement this interface.
 * Note that the interface does not give any guarantees if the processor will
 * modify the predicate in-place or return a new instance.
 *
 * @author bjorncs
 */
@FunctionalInterface
public interface PredicateProcessor {

    /**
     * Processes a predicate.
     *
     * @return the processed predicate.
     */
    Predicate process(Predicate predicate, PredicateOptions options);

}
