// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.language.Language;
import com.yahoo.vespa.indexinglanguage.SimpleAdapterFactory;
import com.yahoo.vespa.indexinglanguage.UpdateAdapter;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import java.util.List;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class GuardTestCase {

    @Test
    public void requireThatAccessorsWork() {
        Expression innerExp = new AttributeExpression("foo");
        GuardExpression exp = new GuardExpression(innerExp);
        assertSame(innerExp, exp.getInnerExpression());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression innerExp = new AttributeExpression("foo");
        Expression exp = new GuardExpression(innerExp);
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new GuardExpression(new AttributeExpression("bar"))));
        assertEquals(exp, new GuardExpression(innerExp));
        assertEquals(exp.hashCode(), new GuardExpression(innerExp).hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new GuardExpression(SimpleExpression.newConversion(DataType.INT, DataType.STRING));
        assertVerify(DataType.INT, exp, DataType.STRING);
        assertVerifyThrows(null, exp, "Expected int input, got null.");
        assertVerifyThrows(DataType.STRING, exp, "Expected int input, got string.");
    }

    @Test
    public void requireThatInputFieldsAreIncludedByDocument() throws ParseException {
        DocumentType docType = new DocumentType("my_input");
        docType.addField(new Field("my_lng", DataType.LONG));
        docType.addField(new Field("my_str", DataType.STRING));

        Document doc = new Document(docType, "doc:scheme:");
        doc.setFieldValue("my_str", new StringFieldValue("69"));
        assertNotNull(doc = Expression.execute(Expression.fromString("guard { input my_str | to_int | attribute my_lng }"), doc));
        assertEquals(new LongFieldValue(69), doc.getFieldValue("my_lng"));
    }

    @Test
    public void requireThatInputFieldsAreIncludedByUpdate() throws ParseException {
        DocumentType docType = new DocumentType("my_input");
        docType.addField(new Field("my_lng", DataType.LONG));
        docType.addField(new Field("my_str", DataType.STRING));

        DocumentUpdate docUpdate = new DocumentUpdate(docType, "doc:scheme:");
        docUpdate.addFieldUpdate(FieldUpdate.createAssign(docType.getField("my_str"), new StringFieldValue("69")));
        assertNotNull(docUpdate = Expression.execute(Expression.fromString("guard { input my_str | to_int | attribute my_lng }"), docUpdate));

        assertEquals(0, docUpdate.getFieldPathUpdates().size());
        assertEquals(1, docUpdate.getFieldUpdates().size());

        FieldUpdate fieldUpd = docUpdate.getFieldUpdate(0);
        assertNotNull(fieldUpd);
        assertEquals(docType.getField("my_lng"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());

        ValueUpdate valueUpd = fieldUpd.getValueUpdate(0);
        assertNotNull(valueUpd);
        assertTrue(valueUpd instanceof AssignValueUpdate);
        assertEquals(new LongFieldValue(69), valueUpd.getValue());
    }

    @Test
    public void requireThatConstFieldsAreIncludedByDocument() throws ParseException {
        DocumentType docType = new DocumentType("my_input");
        docType.addField(new Field("my_lng", DataType.LONG));
        docType.addField(new Field("my_str", DataType.STRING));

        Document doc = new Document(docType, "doc:scheme:");
        doc.setFieldValue("my_str", new StringFieldValue("foo"));
        assertNotNull(doc = Expression.execute(Expression.fromString("guard { now | attribute my_lng }"), doc));
        assertTrue(doc.getFieldValue("my_lng") instanceof LongFieldValue);
    }

    @Test
    public void requireThatConstFieldsAreSkippedByUpdate() throws ParseException {
        DocumentType docType = new DocumentType("my_input");
        docType.addField(new Field("my_int", DataType.INT));
        docType.addField(new Field("my_str", DataType.STRING));

        DocumentUpdate docUpdate = new DocumentUpdate(docType, "doc:scheme:");
        docUpdate.addFieldUpdate(FieldUpdate.createAssign(docType.getField("my_str"), new StringFieldValue("foo")));
        assertNull(Expression.execute(Expression.fromString("guard { now | attribute my_int }"), docUpdate));
    }

    @Test
    public void requireThatLanguageCanBeSetByUpdate() throws ParseException {
        DocumentType docType = new DocumentType("my_input");
        docType.addField(new Field("my_str", DataType.STRING));
        DocumentUpdate docUpdate = new DocumentUpdate(docType, "doc:scheme:");
        docUpdate.addFieldUpdate(FieldUpdate.createAssign(docType.getField("my_str"), new StringFieldValue("foo")));

        SimpleAdapterFactory factory = new SimpleAdapterFactory();
        List<UpdateAdapter> lst = factory.newUpdateAdapterList(docUpdate);
        assertEquals(1, lst.size());

        ExecutionContext ctx = new ExecutionContext(lst.get(0));
        Expression.fromString("guard { 'en' | set_language }").execute(ctx);
        assertEquals(Language.ENGLISH, ctx.getLanguage());
    }
}
