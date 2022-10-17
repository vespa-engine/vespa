// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.search.query.Sorting;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;

import java.util.Comparator;
import java.util.List;

public class SortDataHitSorter {

    public static void sort(HitGroup hitGroup, List<Hit> hits) {
        var sorting = hitGroup.getQuery().getRanking().getSorting();
        var fallbackOrderer = hitGroup.getOrderer();
        if (sorting == null || fallbackOrderer == null) {
            return;
        }
        var fallbackComparator = fallbackOrderer.getComparator();
        hits.sort(getComparator(sorting, fallbackComparator));
    }

    public static boolean isSortable(Hit hit, Sorting sorting) {
        if (sorting == null) {
            return false;
        }
        if (hit instanceof FastHit fhit) {
            return fhit.hasSortData(sorting);
        } else {
            return false;
        }
    }

    public static Comparator<Hit> getComparator(Sorting sorting, Comparator<Hit> fallback) {
        if (fallback == null) {
            return (left, right) -> compareTwo(left, right, sorting);
        } else {
            return (left, right) -> compareWithFallback(left, right, sorting, fallback);
        }
    }

    private static int compareTwo(Hit left, Hit right, Sorting sorting) {
        if (!(left instanceof FastHit fl) || !(right instanceof FastHit fr)) return 0;
        return FastHit.compareSortData(fl, fr, sorting);
    }

    private static int compareWithFallback(Hit left, Hit right, Sorting sorting, Comparator<Hit> fallback) {
        if (!(left instanceof FastHit fl) || !(right instanceof FastHit fr)) {
            return fallback.compare(left, right);
        }
        if (fl.hasSortData(sorting) && fr.hasSortData(sorting)) {
            return FastHit.compareSortData(fl, fr, sorting);
        } else {
            return fallback.compare(left, right);
        }
    }

}
