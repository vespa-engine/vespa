// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.search.query.profile.DimensionBinding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class DimensionBindingTestCase {

    @Test
    void testCombining() {
        assertEquals(binding("a, b, c", "a=1", "b=1", "c=1"),
                binding("a, b", "a=1", "b=1").combineWith(binding("c", "c=1")));

        assertEquals(binding("a, b, c", "a=1", "b=1", "c=1"),
                binding("a, b", "a=1", "b=1").combineWith(binding("a, c", "a=1", "c=1")));

        assertEquals(binding("c, a, b", "c=1", "a=1", "b=1"),
                binding("a, b", "a=1", "b=1").combineWith(binding("c, a", "a=1", "c=1")));

        assertEquals(binding("a, b", "a=1", "b=1"),
                binding("a, b", "a=1", "b=1").combineWith(binding("a, b", "a=1", "b=1")));

        assertEquals(DimensionBinding.invalidBinding,
                binding("a, b", "a=1", "b=1").combineWith(binding("b, a", "a=1", "b=1")));

        assertEquals(binding("a, b", "a=1", "b=1"),
                binding("a, b", "a=1", "b=1").combineWith(binding("b", "b=1")));

        assertEquals(binding("a, b, c", "a=1", "b=1", "c=1"),
                binding("a, b, c", "a=1", "c=1").combineWith(binding("a, b, c", "a=1", "b=1", "c=1")));

        assertEquals(binding("a, b, c", "a=1", "b=1", "c=1"),
                binding("a, c", "a=1", "c=1").combineWith(binding("a, b, c", "a=1", "b=1", "c=1")));
    }

    //     found DimensionBinding [custid_1=yahoo, custid_2=ca, custid_3=sc, custid_4=null, custid_5=null, custid_6=null], combined with DimensionBinding [custid_1=yahoo, custid_2=null, custid_3=sc, custid_4=null, custid_5=null, custid_6=null] to Invalid DimensionBinding
    @Test
    void testCombiningBindingsWithNull() {
        List<String> dimensions = list("a,b");

        Map<String, String> map1 = new HashMap<>();
        map1.put("a", "a1");
        map1.put("b", "b1");

        Map<String, String> map2 = new HashMap<>();
        map2.put("a", "a1");
        map2.put("b", null);

        assertEquals(DimensionBinding.createFrom(dimensions, map1),
                DimensionBinding.createFrom(dimensions, map1).combineWith(DimensionBinding.createFrom(dimensions, map2)));
    }

    private DimensionBinding binding(String dimensions, String ... dimensionPoints) {
        return DimensionBinding.createFrom(list(dimensions), QueryProfileVariantsTestCase.toMap(dimensionPoints));
    }

    private List<String> list(String listString) {
        List<String> l = new ArrayList<>();
        for (String s : listString.split(","))
            l.add(s.trim());
        return l;
    }

}
