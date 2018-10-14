// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.search.query.Sorting;

import java.util.Comparator;

/**
 * Comparator used for ordering hits using the field values and a sorting specification.
 * <p>
 * <b>Note:</b> this comparator imposes orderings that are inconsistent with equals.
 * <p>
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
// Is tested in HitSortSpecOrdererTestCase
public class FieldComparator extends ChainableComparator {

    /** The definition of sorting order */
    private Sorting sorting;

    /** Creates a field comparator using a sort order and having no chained comparator */
    public FieldComparator(Sorting sorting) {
        this(sorting,null);
    }

    /** Creates a field comparator using a sort order with a chained comparator */
    public FieldComparator(Sorting sorting,Comparator<Hit> secondaryComparator) {
        super(secondaryComparator);
        this.sorting = sorting;
    }

    /** Creates a comparator given a sorting, or returns null if the given sorting is null */
    public static FieldComparator create(Sorting sorting) {
        if (sorting==null) return null;
        return new FieldComparator(sorting);
    }

    /**
     * Compares hits based on a sorting specification and values
     * stored in hit fields.0
     * <p>
     * When one of the hits has the requested property and the other
     * has not, the the hit containing the property precedes the one
     * that does not.
     * <p>
     * There is no locale based sorting here, as the backend does
     * not do that either.
     *
     * @return -1, 0, 1 if first should be sorted before, equal to
     * or after second
     */
    @Override
    public int compare(Hit first, Hit second) {
        for (Sorting.FieldOrder fieldOrder : sorting.fieldOrders() ) {
            String fieldName = fieldOrder.getFieldName();
            Object a = getField(first,fieldName);
            Object b = getField(second,fieldName);

            // If either of the values are null, don't touch the ordering
            // This is to avoid problems if the sorting is called before the
            // result is filled.
            if ((a == null) || (b == null)) return 0;

            int x = compareValues(a, b, fieldOrder.getSorter());
            if (x != 0) {
                if (fieldOrder.getSortOrder() == Sorting.Order.DESCENDING)
                    x *= -1;
                return x;
            }
        }
        return super.compare(first,second);
    }

    public Object getField(Hit hit,String key) {
        if ("[relevance]".equals(key)) return hit.getRelevance();
        if ("[rank]".equals(key)) return hit.getRelevance();
        if ("[source]".equals(key)) return hit.getSource();
        return hit.getField(key);
    }

    @SuppressWarnings("rawtypes")
    private int compareValues(Object first, Object second, Sorting.AttributeSorter s) {
        if (first.getClass().isInstance(second)
                && first instanceof Comparable) {
            // We now know:
            // second is of a type which is a subclass of first's type
            // They both implement Comparable
            return s.compare((Comparable)first, (Comparable)second);
        } else {
            return s.compare(first.toString(), second.toString());
        }
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("FieldComparator:");
        if (sorting == null) {
            b.append(" null");
        } else {
            b.append(sorting.toString());
        }
        return b.toString();
    }

}
