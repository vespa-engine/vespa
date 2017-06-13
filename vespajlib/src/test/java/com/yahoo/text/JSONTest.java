// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author bratseth
 */
public class JSONTest {

    @Test
    public void testMapToString() {
        Map<String,Object> map = new LinkedHashMap<>();
        map.put("a \"key\"", 3);
        map.put("key2", "value");
        map.put("key3", 3.3);

        assertEquals("{\"a \\\"key\\\"\":3,\"key2\":\"value\",\"key3\":3.3}", JSON.encode(map));
    }

}
