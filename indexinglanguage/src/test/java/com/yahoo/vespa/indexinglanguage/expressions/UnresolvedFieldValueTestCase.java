// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class UnresolvedFieldValueTestCase {

    @Test
    public void requireThatDataTypeIsUnresolved() {
        assertEquals(UnresolvedDataType.INSTANCE, new UnresolvedFieldValue().getDataType());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void requireThatNoMethodsAreSupported() {
        UnresolvedFieldValue val = new UnresolvedFieldValue();
        try {
            val.printXml(null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        try {
            val.clear();
            fail();
        } catch (UnsupportedOperationException e) {

        }
        try {
            val.assign(new UnresolvedFieldValue());
            fail();
        } catch (UnsupportedOperationException e) {

        }
        try {
            val.serialize(null, null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        try {
            val.deserialize(null, null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }
}
