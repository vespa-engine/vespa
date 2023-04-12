// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.HashSet;

import com.yahoo.processing.request.properties.PropertyMap;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:arnebef@yahoo-inc.com">Arne Bergene Fossaa</a>
 */
@SuppressWarnings({"removal"})

public class SubPropertiesTestCase {

    @Test
    void testSubProperties() {
        PropertyMap map = new PropertyMap() {
            {
                set("a.e", "1");
                set("a.f", 2);
                set("b.e", "3");
                set("f", 3);
                set("e", "2");
                set("d", "a");
            }
        };

        SubProperties sub = new SubProperties("a", map);
        assertEquals("1", sub.get("e"));
        assertEquals(2, sub.get("f"));
        assertNull(sub.get("d"));
        assertEquals(new HashSet<>(Arrays.asList("e", "f")), sub.listProperties("").keySet());
    }

}
