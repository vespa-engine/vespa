// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author arnej
 */
public class ParsedDocumentTestCase {

    @Test
    void fields_can_be_added_once() throws Exception {
        var doc = new ParsedDocument("foo");
        var stringType = ParsedType.fromName("string");
        doc.addField(new ParsedField("bar1", stringType));
        doc.addField(new ParsedField("zap", stringType));
        doc.addField(new ParsedField("bar2", stringType));
        doc.addField(new ParsedField("bar3", stringType));
        var e = assertThrows(IllegalArgumentException.class, () ->
                doc.addField(new ParsedField("zap", stringType)));
        System.err.println("As expected: " + e);
        assertEquals("document 'foo' error: Duplicate (case insensitively) field 'zap' in document type 'foo'", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () ->
                doc.addField(new ParsedField("ZAP", stringType)));
        assertEquals("document 'foo' error: Duplicate (case insensitively) field 'ZAP' in document type 'foo'", e.getMessage());
    }

}
