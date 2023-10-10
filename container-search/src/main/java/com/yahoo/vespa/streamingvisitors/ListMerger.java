// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import java.util.List;
import java.util.ListIterator;
import java.util.ArrayList;

/**
 * A list merger that merges two sorted lists.
 *
 * @author Ulf Carlin
 */
public class ListMerger {

    public static <T extends Comparable<? super T>> void mergeLinkedLists(List<T> to, List<T> from, int maxEntryCount) {
        int entryCount = 0;
        ListIterator<T> i = to.listIterator();
        while (!from.isEmpty()) {
            T fromElement = from.remove(0);
            while (i.hasNext()) {
                T toElement = i.next();
                if (toElement.compareTo(fromElement) > 0) {
                    i.previous();
                    break;
                } else {
                    entryCount++;
                    if (entryCount >= maxEntryCount) {
                        break;
                    }
                }
            }
            if (entryCount >= maxEntryCount) {
                break;
            }
            i.add(fromElement);
            entryCount++;
            if (entryCount >= maxEntryCount) {
                break;
            }
        }
        while (i.hasNext()) {
            i.next();
            i.remove();
        }
    }

    public static <T extends Comparable<? super T>> List<T> mergeIntoArrayList(List<T> l1, List<T> l2, int maxEntryCount) {

        List<T> mergedList = new ArrayList<>();
        ListIterator<T> i1 = l1.listIterator();
        ListIterator<T> i2 = l2.listIterator();

        T e1 = null;
        if (i1.hasNext()) {
            e1 = i1.next();
        }
        T e2 = null;
        if (i2.hasNext()) {
            e2 = i2.next();
        }

        while (e1 != null && e2 != null && mergedList.size() < maxEntryCount) {
            if (e1.compareTo(e2) <= 0) {
                mergedList.add(e1);
                if (i1.hasNext()) {
                    e1 = i1.next();
                } else {
                    e1 = null;
                }
            } else {
                mergedList.add(e2);
                if (i2.hasNext()) {
                    e2 = i2.next();
                } else {
                    e2 = null;
                }
            }
        }

        if (e2 == null) {
            while (e1 != null && mergedList.size() < maxEntryCount) {
                mergedList.add(e1);
                if (i1.hasNext()) {
                    e1 = i1.next();
                } else {
                    e1 = null;
                }
            }
        } else if (e1 == null) {
            while (e2 != null && mergedList.size() < maxEntryCount) {
                mergedList.add(e2);
                if (i2.hasNext()) {
                    e2 = i2.next();
                } else {
                    e2 = null;
                }
            }
        }

        return mergedList;
    }

    public static <T extends Comparable<? super T>> List<T> mergeIntoArrayList(List<T> l1, List<T> l2) {
        return mergeIntoArrayList(l1, l2, Integer.MAX_VALUE);
    }

}
