// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.search.query.Sorting;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A hit orderer which can be assigned to a HitGroup to keep that group's
 * hit sorted in accordance with the sorting specification given when this is created.
 *
 * @author Steinar Knutsen
 */
public class HitSortOrderer extends HitOrderer {

    private final Comparator<Hit> fieldComparator;

    /** Create a sort order from a sorting */
    public HitSortOrderer(Sorting sorting) {
        fieldComparator =
                new MetaHitsFirstComparator(
                        new HitGroupsLastComparator(
                                new FieldComparator(sorting)));
    }

    /**
     * Create a sort order from a comparator.
     * This will be appended to the standard comparators used by this.
     */
    public HitSortOrderer(Comparator<Hit> comparator) {
        fieldComparator = new MetaHitsFirstComparator(new HitGroupsLastComparator(comparator));
    }

    /**
     * Orders the given list of hits according to the sorting given at construction
     *
     * Meta hits are sorted before concrete hits, but have no internal
     * ordering. The sorting is stable.
     */
    public void order(List<Hit> hits) {
        Collections.sort(hits, fieldComparator);
    }

    public Comparator<Hit> getComparator() {
        return fieldComparator;
    }

}
