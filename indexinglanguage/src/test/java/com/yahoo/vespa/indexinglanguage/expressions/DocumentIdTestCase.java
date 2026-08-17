// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author Dainius Jocas
 */
public class DocumentIdTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new DocumentIdExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new DocumentIdExpression());
        assertEquals(exp.hashCode(), new DocumentIdExpression().hashCode());

        Expression withPart = new DocumentIdExpression("namespace");
        assertEquals(withPart, new DocumentIdExpression("namespace"));
        assertEquals(withPart.hashCode(), new DocumentIdExpression("namespace").hashCode());
        assertFalse(exp.equals(withPart));
        assertFalse(withPart.equals(new DocumentIdExpression("group")));
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        assertVerify(AnyDataType.instance, new DocumentIdExpression(), DataType.STRING);
        assertVerify(AnyDataType.instance, new DocumentIdExpression("namespace"), DataType.STRING);
    }

    @Test
    public void requireThatUnknownPartFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentIdExpression("bogus"));
    }

    @Test
    public void requireThatDocumentIdIsSet() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setDocumentId(new DocumentId("id:myns:mytype:n=123:foo"));
        new DocumentIdExpression().execute(ctx);

        FieldValue val = ctx.getCurrentValue();
        assertTrue(val instanceof StringFieldValue);
        assertEquals("id:myns:mytype:n=123:foo", ((StringFieldValue)val).getString());
    }

    @Test
    public void requireThatMissingDocumentIdYieldsEmptyString() {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        new DocumentIdExpression().execute(ctx);

        FieldValue val = ctx.getCurrentValue();
        assertTrue(val instanceof StringFieldValue);
        assertEquals("", ((StringFieldValue)val).getString());
    }

    @Test
    public void requireThatNamespaceIsExtracted() {
        assertEquals("myns", extract("namespace", "id:myns:mytype::foo"));
    }

    @Test
    public void requireThatDocumentTypeIsExtracted() {
        assertEquals("mytype", extract("documenttype", "id:myns:mytype::foo"));
    }

    @Test
    public void requireThatSpecificIsExtracted() {
        assertEquals("foo", extract("specific", "id:myns:mytype::foo"));
    }

    @Test
    public void requireThatGroupIsExtracted() {
        assertEquals("mygroup", extract("group", "id:myns:mytype:g=mygroup:foo"));
    }

    @Test
    public void requireThatNumberIsExtracted() {
        assertEquals("123", extract("number", "id:myns:mytype:n=123:foo"));
    }

    @Test
    public void requireThatMissingGroupOrNumberYieldsEmptyString() {
        assertEquals("", extract("group", "id:myns:mytype::foo"));
        assertEquals("", extract("number", "id:myns:mytype::foo"));
    }

    @Test
    public void requireThatExpressionParsesFromScript() throws com.yahoo.vespa.indexinglanguage.parser.ParseException {
        assertEquals(new DocumentIdExpression(), Expression.fromString("documentid"));
        assertEquals(new DocumentIdExpression("namespace"), Expression.fromString("documentid namespace"));
    }

    private static String extract(String part, String documentId) {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setDocumentId(new DocumentId(documentId));
        new DocumentIdExpression(part).execute(ctx);
        return ((StringFieldValue)ctx.getCurrentValue()).getString();
    }

}
