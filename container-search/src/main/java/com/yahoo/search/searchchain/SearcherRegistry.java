// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Searcher;
import com.yahoo.search.pagetemplates.engine.Resolver;

/**
 * A registry of searchers. This is instantiated and recycled in the context of an owning search chain registry.
 * This class exists for legacy purposes only, to preserve the public API for retrieving searchers from Vespa 4.2.
 *
 * @author bratseth
 */
public class SearcherRegistry extends ComponentRegistry<Searcher> {

    public void register(Searcher searcher) {
        super.register(searcher.getId(), searcher);
    }

}
