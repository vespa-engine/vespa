// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.Collection;
import java.util.Iterator;

/**
 * Utility class which is useful when implementing <code>Comparable</code> and one needs to
 * compare Collections of Comparables as instance variables.
 *
 * @author Einar M R Rosenvinge
 */
public class CollectionComparator {
    /**
     * Compare the arguments. Shorter Collections are always considered
     * smaller than longer Collections. For Collections of equal lengths, the elements
     * are compared one-by-one. Whenever two corresponding elements in the
     * two Collections are non-equal, the method returns. If all elements at
     * corresponding positions in the two Collections are equal, the Collections
     * are considered equal.
     *
     * @param first a Collection of Comparables to be compared
     * @param second a Collection of Comparables to be compared
     * @return 0 if the arguments are equal, -1 if the first argument is smaller, 1 if the second argument is smaller
     * @throws NullPointerException if any of the arguments are null
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static int compare(Collection<? extends Comparable> first, Collection<? extends Comparable> second) {
        if (first.size() < second.size()) {
            return -1;
        }
        if (first.size() > second.size()) {
            return 1;
        }

        //sizes are equal, compare contents
        Iterator<? extends Comparable> firstIt = first.iterator();
        Iterator<? extends Comparable> secondIt = second.iterator();

        while (firstIt.hasNext()) {
            // FIXME: unchecked casting
            Comparable itemFirst = firstIt.next();
            Comparable itemSecond = secondIt.next();
            int comp = itemFirst.compareTo(itemSecond);
            if (comp != 0) {
                return comp;
            }
            //values are equal, continue...
        }

        //we haven't returned yet; contents must be equal:
        return 0;
    }

}
