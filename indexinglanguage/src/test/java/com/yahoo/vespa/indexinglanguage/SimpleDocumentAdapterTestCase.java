// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class SimpleDocumentAdapterTestCase {

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
        Document doc = new Document(docType, "doc:scheme:");
        doc.setFieldValue("foo", foo);

        DocumentAdapter adapter = new SimpleDocumentAdapter(doc);
        assertEquals(fooType, adapter.getInputType(null, "foo"));
        assertEquals(foo, adapter.getInputValue("foo"));
        assertEquals(barType, adapter.getInputType(null, "foo.bar"));
        assertEquals(bar, adapter.getInputValue("foo.bar"));
    }

    @Test
    public void requireThatUnknownFieldsReturnNull() {
        DocumentType docType = new DocumentType("my_doc");
        Document doc = new Document(docType, "doc:scheme:");

        DocumentAdapter adapter = new SimpleDocumentAdapter(doc);
        try {
            adapter.getInputType(null, "foo");
            fail();
        } catch (VerificationException e) {
            assertEquals("Input field 'foo' not found.", e.getMessage());
        }
        assertNull(adapter.getInputValue("foo"));
    }
}
