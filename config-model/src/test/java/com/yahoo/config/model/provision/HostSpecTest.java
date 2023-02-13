// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.provision.HostSpec;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Ulf Lilleengen
 */
public class HostSpecTest {

    @Test
    void testEquals() {
        HostSpec h1 = new HostSpec("foo", Optional.empty());
        HostSpec h2 = new HostSpec("foo", Optional.empty());
        HostSpec h3 = new HostSpec("bar", Optional.empty());

        assertEquals(h1, h1);
        assertEquals(h1, h2);
        assertNotEquals(h1, h3);

        assertEquals(h2, h1);
        assertEquals(h2, h2);
        assertNotEquals(h2, h3);

        assertNotEquals(h3, h1);
        assertNotEquals(h3, h2);
        assertEquals(h3, h3);
    }

}
