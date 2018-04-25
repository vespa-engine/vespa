// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import java.util.Collection;

import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.WordItem;

/**
 * Transform all terms in the incoming query tree and highlight terms to lower
 * case. This searcher is a compatibility layer for customers needing to use
 * FSAs created for pre-5.1 systems.
 *
 * <p>
 * Add this searcher to your search chain before any searcher running
 * case-dependent automata with only lowercased contents, query transformers
 * assuming lowercased input etc. Refer to the Vespa documentation on search
 * chains and search chain ordering.
 * </p>
 *
 * @author Steinar Knutsen
 */
public class AllLowercasingSearcher extends LowercasingSearcher {

    @Override
    public boolean shouldLowercase(WordItem word, IndexFacts.Session settings) {
        return true;
    }

}
