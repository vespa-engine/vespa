// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleDocumentFieldValuesTestCase {

    @Test
    public void requireThatStructFieldsCanBeAccessed() {
        DataType barType = DataType.STRING;
        FieldValue bar = barType.createFieldValue("bar");

        StructDataType fooType = new StructDataType("my_struct");
        fooType.addField(new Field("bar", barType));
        Struct foo = new Struct(fooType);
        foo.setFieldValue("bar", bar);

        DocumentType docType = new DocumentType("my_doc");
        docType.addField("foo", fooType);
        Document doc = new Document(docType, "id:scheme:my_doc::");
        doc.setFieldValue("foo", foo);

        DocumentFieldValues adapter = new SimpleDocumentFieldValues(doc);
        assertEquals(fooType, adapter.getFieldType("foo", null));
        assertEquals(foo, adapter.getInputValue("foo"));
        assertEquals(barType, adapter.getFieldType("foo.bar", null));
        assertEquals(bar, adapter.getInputValue("foo.bar"));
    }

    @Test
    public void requireThatUnknownFieldsReturnNull() {
        DocumentType docType = new DocumentType("my_doc");
        Document doc = new Document(docType, "id:scheme:my_doc::");

        DocumentFieldValues adapter = new SimpleDocumentFieldValues(doc);
        try {
            adapter.getFieldType("foo", null);
            fail();
        } catch (VerificationException e) {
            assertEquals("Invalid expression 'null': Input field 'foo' not found", e.getMessage());
        }
        assertNull(adapter.getInputValue("foo"));
    }
}
