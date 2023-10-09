// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.Phase;
import com.yahoo.search.Searcher;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A named collection of searchers.
 * <p>
 * The searchers may have dependencies which define an ordering
 * of the searchers of this chain.
 * <p>
 * Search chains may inherit the searchers of other chains and modify
 * the inherited set of searchers.
 * <p>
 * Search chains may be versioned. The version and name string combined
 * is an unique identifier of a search chain.
 * <p>
 * A search chain cannot be modified once constructed.
 *
 * @author bratseth
 */
public class SearchChain extends Chain<Searcher> {

    public SearchChain(ComponentId id) {
        this(id, null, null);
    }

    public SearchChain(ComponentId id, Searcher... searchers) {
        this(id, Arrays.asList(searchers));
    }

    public SearchChain(ComponentId id, Collection<Searcher> searchers) {
        this(id, searchers, null);
    }

    /**
     * Creates a search chain.
     * <p>
     * This search chain makes a copy of the given lists before return and does not modify the argument lists.
     * <p>
     * The total set of searchers included in this chain will be
     * <ul>
     * <li>The searchers given in <code>searchers</code>.
     * <li>Plus all searchers returned by {@link #searchers} on all search chains in <code>inherited</code>.
     * If a searcher with a given name is present in the <code>searchers</code> list in any version, that
     * version will be used, and a searcher with that name will never be included from an inherited search chain.
     * If the same searcher exists in multiple inherited chains, the highest version will be used.
     * <li>Minus all searchers, of any version, whose name exists in the <code>excluded</code> list.
     * </ul>
     *
     * @param id        the id of this search chain
     * @param searchers the searchers of this chain, or null if none
     * @param phases    the phases of this chain
     */
    public SearchChain(ComponentId id, Collection<Searcher> searchers, Collection<Phase> phases) {
        super(id, searchers, phases);
    }

    /** For internal use only! */
    public SearchChain(Chain<Searcher> chain) {
        super(chain.getId(), chain.components());
    }

    /**
     * Returns an unmodifiable list of the searchers this search chain executs, in resolved execution order.
     * This includes all inherited (and not excluded) searchers.
     */
    public List<Searcher> searchers() {
        return components();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("search ");
        b.append(super.toString());
        return b.toString();
    }
}
