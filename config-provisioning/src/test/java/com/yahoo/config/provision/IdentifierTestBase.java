// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generic test for identifiers such as {@link Environment} and {@link RegionName}.
 * @author Ulf Lilleengen
 */
public abstract class IdentifierTestBase<ID_TYPE> {

    protected abstract ID_TYPE createInstance(String id);
    protected abstract ID_TYPE createDefaultInstance();
    protected abstract boolean isDefault(ID_TYPE instance);

    @Test
    void testDefault() {
        ID_TYPE def = createDefaultInstance();
        ID_TYPE def2 = createInstance("default");
        ID_TYPE notdef = createInstance("default2");
        assertTrue(isDefault(def));
        assertTrue(isDefault(def2));
        assertFalse(isDefault(notdef));
        assertEquals(def, def2);
        assertNotEquals(def2, notdef);
    }

    @Test
    void testEquals() {
        assertEquals(Set.of(createInstance("foo"), createInstance("bar"), createInstance("baz")),
                               new HashSet<>(List.of(createInstance("foo"), createInstance("foo"), createInstance("bar"), createInstance("baz"))));
    }

}
