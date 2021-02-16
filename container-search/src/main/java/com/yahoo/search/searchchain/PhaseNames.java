// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

/**
 * Helper class for ordering searchers. Searchers may use these names in their
 * {@literal @}Before and {@literal @}After annotations, though in general
 * a searcher should depend on some explicit functionality, not these
 * checkpoints.
 *
 * @author Steinar Knutsen
 */
public final class PhaseNames {

    private PhaseNames() {
    }

    /**
     * A checkpoint where the query is not yet transformed in any way. RAW_QUERY
     * is the first checkpoint not provided by some searcher.
     */
    public static final String RAW_QUERY = "rawQuery";

    /**
     * A checkpoint where as many query transformers as practically possible has
     * been run. TRANSFORMED_QUERY is the first checkpoint after RAW_QUERY.
     */
    public static final String TRANSFORMED_QUERY = "transformedQuery";

    /**
     * A checkpoint where results from different backends have been flattened
     * into a single result. BLENDED_RESULT is the first checkpoint after
     * TRANSFORMED_QUERY.
     */
    public static final String BLENDED_RESULT = "blendedResult";

    /**
     * A checkpoint where data from different backends are not yet merged.
     * UNBLENDED_RESULT is the first checkpoint after BLENDED_RESULT.
     */
    public static final String UNBLENDED_RESULT = "unblendedResult";

    /**
     * The last checkpoint in a search chain not provided by any searcher.
     */
    public static final String BACKEND = "backend";

}
