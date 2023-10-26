// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import java.util.Comparator;

/**
 * Superclass of hit comparators which delegates comparisons of hits which are
 * equal according to this comparator, to a secondary comparator.
 *
 * @author bratseth
 */
public abstract class ChainableComparator implements Comparator<Hit> {

    private final Comparator<Hit> secondaryComparator;

    /** Creates this comparator, given a secondary comparator, or null if there is no secondary */
    public ChainableComparator(Comparator<Hit> secondaryComparator) {
        this.secondaryComparator=secondaryComparator;
    }

    /** Returns the comparator to use to compare hits which are equal according to this, or null if none */
    public Comparator<Hit> getSecondaryComparator() { return secondaryComparator; }

    /**
     * Returns the comparison form the secondary comparison, or 0 if the secondary is null.
     * When overriding this in the subclass, always <code>return super.compare(first,second)</code>
     * at the end of the subclass' implementation.
     */
    @Override
    public int compare(Hit first, Hit second) {
        if (secondaryComparator == null) return 0;
        return secondaryComparator.compare(first, second);
    }

}
