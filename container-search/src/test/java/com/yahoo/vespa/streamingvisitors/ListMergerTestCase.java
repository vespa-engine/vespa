// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import org.junit.Test;

import java.util.List;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Carlin
 */
public class ListMergerTestCase {

    private void initializeLists(List<String> list1, List<String> list2, int entryCount, int padding) {
        for (int i = 0; i < entryCount; i++) {
            if ((i % 2) == 0) {
                list1.add("String " + String.format("%0" + padding + "d", (i+1)));
            } else {
                list2.add("String " + String.format("%0" + padding + "d", (i+1)));
            }
        }
    }

    private void verifyList(List<String> list, int entryCount, int padding) {
        assertEquals(entryCount, list.size());
        for (int i = 0; i < entryCount; i++) {
            assertEquals("String " + String.format("%0" + padding + "d", (i+1)), list.get(i));
        }
    }

    @Test
    public void testMergeLists() {
        int entryCount = 6;
        int padding = (int)Math.log10(entryCount) + 1;

        List<String> list1 = new LinkedList<>();
        List<String> list2 = new LinkedList<>();
        initializeLists(list1, list2, entryCount, padding);

        List<String> newList = ListMerger.mergeIntoArrayList(list1, list2);
        verifyList(newList, entryCount, padding);

        newList = ListMerger.mergeIntoArrayList(list1, list2, entryCount/2);
        verifyList(newList, entryCount/2, padding);

        ListMerger.mergeLinkedLists(list1, list2, entryCount/2);
        verifyList(list1, entryCount/2, padding);
    }

    @Test
    public void testMergeListsReversed() {
        int entryCount = 6;
        int padding = (int)Math.log10(entryCount) + 1;

        List<String> list1 = new LinkedList<>();
        List<String> list2 = new LinkedList<>();
        initializeLists(list2, list1, entryCount, padding);

        List<String> newList = ListMerger.mergeIntoArrayList(list1, list2);
        verifyList(newList, entryCount, padding);

        newList = ListMerger.mergeIntoArrayList(list1, list2, entryCount/2);
        verifyList(newList, entryCount/2, padding);

        ListMerger.mergeLinkedLists(list1, list2, entryCount/2);
        verifyList(list1, entryCount/2, padding);
    }

}
