// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.testing.EqualsTester;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Generic test for identifiers such as {@link Environment} and {@link RegionName}.
 * @author Ulf Lilleengen
 * @since 5.23
 */
public abstract class IdentifierTestBase<ID_TYPE> {

    protected abstract ID_TYPE createInstance(String id);
    protected abstract ID_TYPE createDefaultInstance();
    protected abstract boolean isDefault(ID_TYPE instance);

    @Test
    public void testDefault() {
        ID_TYPE def = createDefaultInstance();
        ID_TYPE def2 = createInstance("default");
        ID_TYPE notdef = createInstance("default2");
        assertTrue(isDefault(def));
        assertTrue(isDefault(def2));
        assertFalse(isDefault(notdef));
        assertThat(def, is(def2));
        assertThat(def2, is(not(notdef)));
    }

    @Test
    public void testEquals() {
        new EqualsTester()
                .addEqualityGroup(createInstance("foo"), createInstance("foo"))
                .addEqualityGroup(createInstance("bar"))
                .addEqualityGroup(createInstance("baz"))
                .testEquals();
    }
}
