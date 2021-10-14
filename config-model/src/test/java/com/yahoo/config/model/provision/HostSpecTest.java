// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.provision.HostSpec;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class HostSpecTest {

    @Test
    public void testEquals() {
        HostSpec h1 = new HostSpec("foo", List.of(), Optional.empty());
        HostSpec h2 = new HostSpec("foo", List.of(), Optional.empty());
        HostSpec h3 = new HostSpec("foo", List.of("my", "alias"), Optional.empty());
        HostSpec h4 = new HostSpec("bar", List.of(), Optional.empty());

        assertTrue(h1.equals(h1));
        assertTrue(h1.equals(h2));
        assertTrue(h1.equals(h3));
        assertFalse(h1.equals(h4));

        assertTrue(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h2.equals(h3));
        assertFalse(h2.equals(h4));

        assertTrue(h3.equals(h1));
        assertTrue(h3.equals(h2));
        assertTrue(h3.equals(h3));
        assertFalse(h3.equals(h4));

        assertFalse(h4.equals(h1));
        assertFalse(h4.equals(h2));
        assertFalse(h4.equals(h3));
        assertTrue(h4.equals(h4));
    }

}
