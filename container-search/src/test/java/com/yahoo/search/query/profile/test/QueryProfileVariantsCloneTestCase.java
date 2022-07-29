// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;


import com.yahoo.search.query.profile.DimensionValues;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Tony Vaagenes
 */
public class QueryProfileVariantsCloneTestCase {

    @Test
    void test_that_interior_and_leaf_values_on_a_path_are_preserved_when_cloning() {
        Map<String, String> dimensionBinding = createDimensionBinding("location", "norway");

        QueryProfile profile = new QueryProfile("profile");
        profile.setDimensions(keys(dimensionBinding));

        DimensionValues dimensionValues = DimensionValues.createFrom(values(dimensionBinding));
        profile.set("interior.leaf", "leafValue", dimensionValues, null);
        profile.set("interior", "interiorValue", dimensionValues, null);

        CompiledQueryProfile clone = profile.compile(null).clone();

        assertEquals(profile.get("interior", dimensionBinding, null),
                clone.get("interior", dimensionBinding));

        assertEquals(profile.get("interior.leaf", dimensionBinding, null),
                clone.get("interior.leaf", dimensionBinding));
    }


    private static Map<String,String> createDimensionBinding(String dimension, String value) {
        Map<String, String> dimensionBinding = new HashMap<>();
        dimensionBinding.put(dimension, value);
        return Collections.unmodifiableMap(dimensionBinding);
    }

    private static String[] keys(Map<String, String> map) {
        return map.keySet().toArray(new String[0]);
    }

    private static String[] values(Map<String, String> map) {
        return map.values().toArray(new String[0]);
    }
}
