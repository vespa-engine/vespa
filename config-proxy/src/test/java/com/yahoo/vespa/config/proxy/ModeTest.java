// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class ModeTest {

    @Test
    public void basic() {
        Mode mode = new Mode();
        assertModeName(Mode.ModeName.DEFAULT, mode);
        assertTrue(mode.isDefault());

        mode = new Mode("");
        assertModeName(Mode.ModeName.DEFAULT, mode);
        assertTrue(mode.isDefault());

        mode = new Mode(Mode.ModeName.DEFAULT.name());
        assertModeName(Mode.ModeName.DEFAULT, mode);
        assertTrue(mode.isDefault());

        mode = new Mode(Mode.ModeName.MEMORYCACHE.name());
        assertModeName(Mode.ModeName.MEMORYCACHE, mode);
        assertTrue(mode.isMemoryCache());

        assertTrue(new Mode(Mode.ModeName.DEFAULT.name()).requiresConfigSource());

        assertFalse(new Mode(Mode.ModeName.MEMORYCACHE.name()).requiresConfigSource());

        Set<String> modes = new HashSet<>();
        for (Mode.ModeName modeName : Mode.ModeName.values()) {
            modes.add(modeName.name().toLowerCase());
        }

        assertThat(Mode.modes(), is(modes));

        assertFalse(Mode.validModeName("foo"));

        assertThat(mode.toString(), is(Mode.ModeName.MEMORYCACHE.name().toLowerCase()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void failWhenInvalidMode() {
        new Mode("invalid_mode");
    }

    private void assertModeName(Mode.ModeName expected, Mode actual) {
        assertThat(actual.name(), is(expected.name().toLowerCase()));
    }
}
