// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.stream;

import com.google.common.collect.Lists;
import com.yahoo.stream.CustomCollectors.DuplicateKeyException;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.yahoo.stream.CustomCollectors.toCustomMap;
import static com.yahoo.stream.CustomCollectors.toLinkedMap;
import static java.util.function.Function.identity;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author gjoranv
 */
public class CustomCollectorsTest {

    @Test
    public void linked_map_collector_returns_map_with_insertion_order() {
        List<String> stringList = numberList();
        Map<String, String> orderedMap = stringList.stream().collect(toLinkedMap(identity(), identity()));
        int i = 0;
        for (String val : orderedMap.keySet()) {
            assertEquals(stringList.get(i), val);
            i++;
        }
    }

    @Test
    public void custom_map_collector_returns_map_from_given_supplier() {
        List<String> stringList = numberList();
        Map<String, String> customMap = stringList.stream().collect(toCustomMap(identity(), identity(), CustomHashMap::new));

        assertEquals(CustomHashMap.class, customMap.getClass());
    }

    @Test
    public void custom_map_collector_throws_exception_upon_duplicate_keys() {
        List<String> duplicates = Lists.newArrayList("same", "same");

        try {
            duplicates.stream().collect(toCustomMap(Function.identity(), Function.identity(), HashMap::new));
            fail();
        } catch (DuplicateKeyException e) {

        }
    }

    private static List<String> numberList() {
        return Lists.newArrayList("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten");
    }

    private static class CustomHashMap<K,V> extends HashMap<K,V> {
        private static final long serialVersionUID = 1L;  // To avoid compiler warning
    }

}
