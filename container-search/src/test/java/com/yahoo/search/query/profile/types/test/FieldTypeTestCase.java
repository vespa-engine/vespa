// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types.test;

import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class FieldTypeTestCase {

    @Test
    void testConvertToFromString() {
        QueryProfileTypeRegistry registry = new QueryProfileTypeRegistry();
        registry.register(new QueryProfileType("foo"));

        assertEquals("string", FieldType.fromString("string", registry).stringValue());
        assertEquals("boolean", FieldType.fromString("boolean", registry).stringValue());
        assertEquals("query-profile", FieldType.fromString("query-profile", registry).stringValue());
        assertEquals("query-profile:foo", FieldType.fromString("query-profile:foo", registry).stringValue());
    }

}
