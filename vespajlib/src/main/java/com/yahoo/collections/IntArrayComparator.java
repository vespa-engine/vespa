// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

/**
 * Utility class which is useful when implementing <code>Comparable</code> and one needs to
 * compare int arrays as instance variables.
 *
 * @author Einar M R Rosenvinge
 */
public class IntArrayComparator {

    /**
     * Compare the arguments. Shorter arrays are always considered
     * smaller than longer arrays. For arrays of equal lengths, the elements
     * are compared one-by-one. Whenever two corresponding elements in the
     * two arrays are non-equal, the method returns. If all elements at
     * corresponding positions in the two arrays are equal, the arrays
     * are considered equal.
     *
     * @param first an int array to be compared
     * @param second an int array to be compared
     * @return 0 if the arguments are equal, -1 if the first argument is smaller, 1 if the second argument is smaller
     * @throws NullPointerException if any of the arguments are null
     */
    public static int compare(int[] first, int[] second) {
        if (first.length < second.length) {
            return -1;
        }
        if (first.length > second.length) {
            return 1;
        }

        // lengths are equal, compare contents
        for (int i = 0; i < first.length; i++) {
            if (first[i] < second[i]) {
                return -1;
            } else if (first[i] > second[i]) {
                return 1;
            }
            //values at index i are equal, continue...
        }

        // we haven't returned yet; contents must be equal:
        return 0;
    }

}
