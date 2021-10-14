// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class TemporaryStructuredDataTypeTestCase {
    @Test
    public void basic() {
        TemporaryStructuredDataType type = TemporaryStructuredDataType.create("banana");
        assertEquals("banana", type.getName());
        int originalId = type.getId();
        type.setName("apple");
        assertEquals("apple", type.getName());
        assertNotEquals(originalId, type.getId());
    }
}
