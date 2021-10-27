// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import org.junit.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class AbstractFilteringListTest {

    @Test
    public void testOperations() {
        MyList list = MyList.of("ABC", "abc", "cba", "bbb", "ABC");

        assertEquals(List.of("ABC", "abc", "cba", "bbb", "ABC"),
                     list.first(100).asList());

        assertEquals(List.of("ABC", "abc"),
                     list.first(2).asList());

        assertEquals(List.of("cba", "bbb", "ABC"),
                     list.not().first(2).asList());

        assertEquals(List.of("ABC", "ABC", "abc", "bbb"),
                     list.sortedBy(Comparator.naturalOrder()).first(4).asList());

        assertEquals(List.of("abc", "cba", "bbb"),
                     list.allLowercase().asList());

        assertEquals(List.of("ABC", "ABC"),
                     list.not().allLowercase().asList());

        assertEquals(List.of("abc", "cba", "bbb"),
                     list.not().not().allLowercase().asList());

        assertEquals(List.of(3, 3, 3, 3, 3),
                     list.mapToList(String::length));

        assertEquals(List.of("ABC", "ABC"),
                     list.in(MyList.of("ABC", "CBA")).asList());

        assertEquals(List.of("abc", "cba", "bbb"),
                     list.not().in(MyList.of("ABC", "CBA")).asList());

        assertEquals(List.of("ABC", "abc", "cba", "bbb", "ABC", "aaa", "ABC"),
                     list.and(MyList.of("aaa", "ABC")).asList());
    }

    private static class MyList extends AbstractFilteringList<String, MyList> {

        private MyList(List<String> strings, boolean negate) {
            super(strings, negate, MyList::new);
        }

        private static MyList of(String... strings) {
            return new MyList(List.of(strings), false);
        }

        private MyList allLowercase() {
            return matching(string -> string.toLowerCase(Locale.ROOT).equals(string));
        }
    }

}
