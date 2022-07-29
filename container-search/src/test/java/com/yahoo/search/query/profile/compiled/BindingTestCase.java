// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.compiled;

import com.yahoo.search.query.profile.DimensionBinding;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class BindingTestCase {

    @Test
    void testGeneralizes() {
        Map<String, String> m1 = new HashMap<>();
        m1.put("a", "a1");
        m1.put("b", "b1");
        m1.put("c", "c1");
        m1.put("e", "e1");

        Map<String, String> m2 = new HashMap<>();
        m2.put("a", "a2");
        m2.put("b", "b2");
        m2.put("c", "c2");
        m2.put("d", "d2");
        m2.put("e", "e2");

        Map<String, String> m3 = new HashMap<>();
        m3.put("a", "a1");
        m3.put("b", "b1");
        m3.put("c", "c1");
        m3.put("d", "d1");
        m3.put("e", "e1");

        Map<String, String> m4 = new HashMap<>();
        m4.put("a", "a1");
        m4.put("b", "b1");
        m4.put("c", "c1");
        m4.put("d", "d2");
        m4.put("e", "e1");

        Binding b1 = Binding.createFrom(DimensionBinding.createFrom(m1));
        Binding b2 = Binding.createFrom(DimensionBinding.createFrom(m2));
        Binding b3 = Binding.createFrom(DimensionBinding.createFrom(m3));
        Binding b4 = Binding.createFrom(DimensionBinding.createFrom(m4));
        assertFalse(b1.generalizes(b2));
        assertTrue(b1.generalizes(b3));
        assertTrue(b1.generalizes(b4));
    }

}
