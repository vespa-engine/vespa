// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.vespa.indexinglanguage.SimpleDocumentAdapter;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class InputTestCase {

    @Test
    public void requireThatAccessorsWork() {
        InputExpression exp = new InputExpression("foo");
        assertEquals("foo", exp.getFieldName());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new InputExpression("foo");
        assertNotEquals(exp, new Object());
        assertNotEquals(exp, new InputExpression("bar"));
        assertEquals(exp, new InputExpression("foo"));
        assertEquals(exp.hashCode(), new InputExpression("foo").hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        SimpleTestAdapter adapter = new SimpleTestAdapter(new Field("foo", DataType.STRING));
        adapter.setOutputValue(null, "foo", new StringFieldValue("69"));
        assertEquals(DataType.STRING, new InputExpression("foo").verify(adapter));
        try {
            new InputExpression("bar").verify(adapter);
            fail();
        } catch (VerificationException e) {
            assertEquals("Field 'bar' not found.", e.getMessage());
        }
    }

    @Test
    public void requireThatFieldIsRead() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter(new Field("in", DataType.STRING)));
        ctx.setOutputValue(null, "in", new StringFieldValue("69"));
        new InputExpression("in").execute(ctx);

        assertEquals(new StringFieldValue("69"), ctx.getValue());
    }

    @Test
    public void requireThatStructFieldsCanBeRead() {
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

        ExecutionContext ctx = new ExecutionContext(new SimpleDocumentAdapter(doc));
        assertEquals(foo, new InputExpression("foo").execute(ctx));
        assertEquals(bar, new InputExpression("foo.bar").execute(ctx));
    }
}
