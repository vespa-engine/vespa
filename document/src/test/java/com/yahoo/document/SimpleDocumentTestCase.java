// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.IntegerFieldValue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleDocumentTestCase {

    @Test
    public void requireThatAccessorsWorks() {
        DocumentType type = new DocumentType("test");
        type.addField("int", DataType.INT);
        Document doc = new Document(type, "id:ns:test::");
        SimpleDocument simple = new SimpleDocument(doc);

        assertNull(simple.get("int"));
        assertNull(doc.getFieldValue("int"));

        simple.set("int", 69);
        assertEquals(69, simple.get("int"));
        assertEquals(new IntegerFieldValue(69), doc.getFieldValue("int"));

        simple.remove("int");
        assertNull(simple.get("int"));
        assertNull(doc.getFieldValue("int"));
    }
}
