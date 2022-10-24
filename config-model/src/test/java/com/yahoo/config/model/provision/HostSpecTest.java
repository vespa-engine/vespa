// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.provision.HostSpec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Ulf Lilleengen
 */
public class HostSpecTest {

    @Test
    void testEquals() {
        HostSpec h1 = new HostSpec("foo", List.of(), Optional.empty());
        HostSpec h2 = new HostSpec("foo", List.of(), Optional.empty());
        HostSpec h3 = new HostSpec("foo", List.of("my", "alias"), Optional.empty());
        HostSpec h4 = new HostSpec("bar", List.of(), Optional.empty());

        assertEquals(h1, h1);
        assertEquals(h1, h2);
        assertEquals(h1, h3);
        assertNotEquals(h1, h4);

        assertEquals(h2, h1);
        assertEquals(h2, h2);
        assertEquals(h2, h3);
        assertNotEquals(h2, h4);

        assertEquals(h3, h1);
        assertEquals(h3, h2);
        assertEquals(h3, h3);
        assertNotEquals(h3, h4);

        assertNotEquals(h4, h1);
        assertNotEquals(h4, h2);
        assertNotEquals(h4, h3);
        assertEquals(h4, h4);
    }

}
