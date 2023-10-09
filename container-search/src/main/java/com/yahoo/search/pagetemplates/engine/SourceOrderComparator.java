// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine;

import com.yahoo.search.result.ChainableComparator;
import com.yahoo.search.result.Hit;

import java.util.Comparator;
import java.util.List;

/**
 * @author bratseth
 */
class SourceOrderComparator extends ChainableComparator {

    private final List<String> sourceOrder;

    /**
     * Creates a source order comparator, with no secondary
     *
     * @param sourceOrder the sort order of list names. This list gets owned by this and must not be modified
     */
    public SourceOrderComparator(List<String> sourceOrder) {
        this(sourceOrder,null);
    }

    /**
     * Creates a source order comparator, with an optional secondary comparator.
     *
     * @param sourceOrder the sort order of list names. This list gets owned by this and must not be modified
     * @param secondaryComparator the comparator to use as secondary, or null to use the intrinsic hit order
     */
    public SourceOrderComparator(List<String> sourceOrder,Comparator<Hit> secondaryComparator) {
        super(secondaryComparator);
        this.sourceOrder=sourceOrder;
    }

    @Override
    public int compare(Hit h1,Hit h2) {
        int primaryOrder=sourceOrderCompare(h1,h2);
        if (primaryOrder!=0) return primaryOrder;

        return super.compare(h1,h2);
    }

    private int sourceOrderCompare(Hit h1,Hit h2) {
        String h1Source=h1.getSource();
        String h2Source=h2.getSource();

        if (h1Source==null && h2Source==null) return 0;
        if (h1Source==null) return 1; // No source -> last
        if (h2Source==null) return -1; // No source -> last

        if (h1Source.equals(h2Source)) return 0;

        return sourceOrder.indexOf(h1Source)-sourceOrder.indexOf(h2Source);
    }

}
