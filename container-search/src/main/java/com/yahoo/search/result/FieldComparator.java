// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Inspectable;
import com.yahoo.search.query.Sorting;

import java.util.Comparator;

/**
 * Comparator used for ordering hits using the field values and a sorting specification.
 * Note: This comparator imposes orderings that are inconsistent with equals.
 *
 * @author Steinar Knutsen
 */
// Is tested in HitSortSpecOrdererTestCase
public class FieldComparator extends ChainableComparator {

    /** The definition of sorting order */
    private final Sorting sorting;

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
        if (sorting == null) return null;
        return new FieldComparator(sorting);
    }

    /**
     * Compares hits based on a sorting specification and values
     * stored in hit fields.0
     * <p>
     * When one of the hits has the requested property and the other
     * has not, the hit containing the property precedes the one
     * that does not.
     * <p>
     * There is no locale based sorting here, as the backend does
     * not do that either.
     *
     * @return -1, 0, 1 if first should be sorted before, equal to or after second
     */
    @Override
    public int compare(Hit first, Hit second) {
        for (Sorting.FieldOrder fieldOrder : sorting.fieldOrders() ) {
            String fieldName = fieldOrder.getFieldName();
            Object a = getField(first,fieldName);
            Object b = getField(second,fieldName);

            int x = compareValues(a, b, fieldOrder.getSorter());
            if (x != 0) {
                if (fieldOrder.getSortOrder() == Sorting.Order.DESCENDING)
                    x *= -1;
                return x;
            }
        }
        return super.compare(first, second);
    }

    private Object getSubField(Object field, String key) {
        if (field instanceof Inspectable) {
            Inspector top = ((Inspectable)field).inspect();
            int firstDot = key.indexOf('.');
            if (firstDot > 0) {
                Inspector sub = top.field(key.substring(0, firstDot));
                if (sub.valid()) {
                    return getSubField(sub, key.substring(firstDot + 1));
                }
            }
            Inspector sub = top.field(key);
            if (sub.valid()) {
                return switch (sub.type()) {
                    case EMPTY -> null;
                    case BOOL -> (sub.asBool() ? Boolean.TRUE : Boolean.FALSE);
                    case LONG -> sub.asLong();
                    case DOUBLE -> sub.asDouble();
                    case STRING -> sub.asString();
                    default -> sub.toString();
                };
            }
        }
        // fallback value
        return field;
    }

    public Object getField(Hit hit, String key) {
        if ("[relevance]".equals(key)) return hit.getRelevance();
        if ("[rank]".equals(key)) return hit.getRelevance();
        if ("[source]".equals(key)) return hit.getSource();
        // missing: "[docid]"
        int firstDot = key.indexOf('.');
        if (firstDot > 0 && hit.getField(key) == null) {
            String keyPrefix = key.substring(0, firstDot);
            String keySuffix = key.substring(firstDot + 1);
            Object a = hit.getField(keyPrefix);
            Object b = getSubField(a, keySuffix);
            return b;
        }
        return hit.getField(key);
    }

    @SuppressWarnings("rawtypes")
    private int compareValues(Object first, Object second, Sorting.AttributeSorter s) {
        if (first == null)
            return second == null ? 0 : -1;
        else if (second == null)
            return 1;

        if (first.getClass().isInstance(second) && first instanceof Comparable) {
            // We now know:
            // Second is of a type which is a subclass of first's type
            // They both implement Comparable
            return s.compare((Comparable)first, (Comparable)second);
        } else {
            return s.compare(first.toString(), second.toString());
        }
    }

    @Override
    public String toString() {
        return "FieldComparator:" + (sorting == null ? " null" : sorting.toString());
    }

}
