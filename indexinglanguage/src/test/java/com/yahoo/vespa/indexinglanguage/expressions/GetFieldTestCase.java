// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.vespa.indexinglanguage.SimpleDocumentFieldValues;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class GetFieldTestCase {

    @Test
    public void requireThatAccessorsWork() {
        GetFieldExpression exp = new GetFieldExpression("foo");
        assertEquals("foo", exp.getFieldName());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new GetFieldExpression("foo");
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new GetFieldExpression("bar")));
        assertEquals(exp, new GetFieldExpression("foo"));
        assertEquals(exp.hashCode(), new GetFieldExpression("foo").hashCode());
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

        ExecutionContext ctx = new ExecutionContext(new SimpleDocumentFieldValues(doc));
        assertEquals(bar, new StatementExpression(new InputExpression("foo"),
                                                  new GetFieldExpression("bar")).execute(ctx));
    }

    @Test
    public void requireThatIllegalInputThrows() {
        try {
            new GetFieldExpression("foo").execute(new StringFieldValue("bar"));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("In get_field foo: Expected structured input, got string", e.getMessage());
        }
    }

    @Test
    public void requireThatUnknownFieldThrows() {
        try {
            new GetFieldExpression("foo").execute(new StructDataType("my_struct").createFieldValue());
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("In get_field foo: Field 'foo' not found in struct type 'my_struct'", e.getMessage());
        }
    }
}
