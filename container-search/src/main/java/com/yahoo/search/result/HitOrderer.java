// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import java.util.Comparator;
import java.util.List;

/**
 * A class capable of ordering a list of hits
 *
 * @author bratseth
 */
public abstract class HitOrderer {

    /** Orders the given list of hits */
    public abstract void order(List<Hit> hits);

    /**
     * Returns the Comparator that this HitOrderer uses internally to sort hits. Returns null if no Comparator is used.
     * <p>
     * This default implementation returns null.
     *
     * @return the Comparator used to order hits, or null
     */
    public Comparator<Hit> getComparator() {
        return null;
    }

}
