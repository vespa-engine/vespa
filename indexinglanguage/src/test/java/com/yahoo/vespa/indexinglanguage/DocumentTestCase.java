// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.*;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class DocumentTestCase {

    @Test
    public void requireThatArrayOfStructIsProcessedCorrectly() throws ParseException {
        DocumentType docType = new DocumentType("my_input");
        docType.addField(new Field("my_str", DataType.getArray(DataType.STRING)));
        docType.addField(new Field("my_pos", DataType.getArray(PositionDataType.INSTANCE)));

        Document doc = new Document(docType, "doc:scheme:");
        Array<StringFieldValue> arr = new Array<>(DataType.getArray(DataType.STRING));
        arr.add(new StringFieldValue("6;9"));
        doc.setFieldValue("my_str", arr);

        assertNotNull(doc = Expression.execute(Expression.fromString("input my_str | for_each { to_pos } | index my_pos"), doc));
        assertNotNull(doc.getFieldValue("my_str"));
        FieldValue val = doc.getFieldValue("my_pos");
        assertNotNull(val);
        assertEquals(DataType.getArray(PositionDataType.INSTANCE), val.getDataType());
        assertTrue(val instanceof Array);
        arr = (Array)val;
        assertEquals(1, arr.size());
        assertNotNull(val = arr.getFieldValue(0));
        assertEquals(PositionDataType.INSTANCE, val.getDataType());
        assertTrue(val instanceof Struct);
        Struct pos = (Struct)val;
        assertEquals(new IntegerFieldValue(6), PositionDataType.getXValue(pos));
        assertEquals(new IntegerFieldValue(9), PositionDataType.getYValue(pos));
    }

    @Test
    public void requireThatConcatenationWorks() throws ParseException {
        DocumentType docType = new DocumentType("my_input");
        docType.addField(new Field("arr_a", DataType.getArray(DataType.STRING)));
        docType.addField(new Field("arr_b", DataType.getArray(DataType.STRING)));
        docType.addField(new Field("out", DataType.getArray(DataType.STRING)));

        Expression exp = Expression.fromString("input arr_a . input arr_b | index out");
        {
            Document doc = new Document(docType, "doc:scheme:");
            assertNotNull(doc = Expression.execute(exp, doc));
            FieldValue val = doc.getFieldValue("out");
            assertNotNull(val);
            assertEquals(DataType.getArray(DataType.STRING), val.getDataType());
            assertTrue(val instanceof Array);
            Array arr = (Array)val;
            assertEquals(0, arr.size());
        }
        {
            Document doc = new Document(docType, "doc:scheme:");
            Array<StringFieldValue> arr = new Array<>(DataType.getArray(DataType.STRING));
            arr.add(new StringFieldValue("a1"));
            doc.setFieldValue("arr_a", arr);

            assertNotNull(doc = Expression.execute(exp, doc));
            FieldValue val = doc.getFieldValue("out");
            assertNotNull(val);
            assertEquals(DataType.getArray(DataType.STRING), val.getDataType());
            assertTrue(val instanceof Array);
            arr = (Array)val;
            assertEquals(1, arr.size());
        }
        {
            Document doc = new Document(docType, "doc:scheme:");
            Array<StringFieldValue> arr = new Array<>(DataType.getArray(DataType.STRING));
            arr.add(new StringFieldValue("a1"));
            arr.add(new StringFieldValue("a2"));
            doc.setFieldValue("arr_a", arr);
            arr = new Array<StringFieldValue>(DataType.getArray(DataType.STRING));
            arr.add(new StringFieldValue("b1"));
            doc.setFieldValue("arr_b", arr);

            assertNotNull(doc = Expression.execute(exp, doc));
            FieldValue val = doc.getFieldValue("out");
            assertNotNull(val);
            assertEquals(DataType.getArray(DataType.STRING), val.getDataType());
            assertTrue(val instanceof Array);
            arr = (Array)val;
            assertEquals(3, arr.size());
        }
    }

    @Test
    public void requireThatConcatenationOfEmbracedStatementsWorks() throws ParseException {
        DocumentType docType = new DocumentType("my_input");
        docType.addField(new Field("str_a", DataType.STRING));
        docType.addField(new Field("str_b", DataType.STRING));
        docType.addField(new Field("out", DataType.getArray(DataType.STRING)));

        Expression exp = Expression.fromString("(input str_a | split ',') . (input str_b | split ',') | index out");
        {
            Document doc = new Document(docType, "doc:scheme:");
            assertNotNull(doc = Expression.execute(exp, doc));
            FieldValue val = doc.getFieldValue("out");
            assertNotNull(val);
            assertEquals(DataType.getArray(DataType.STRING), val.getDataType());
            assertTrue(val instanceof Array);
            Array arr = (Array)val;
            assertEquals(0, arr.size());
        }
        {
            Document doc = new Document(docType, "doc:scheme:");
            doc.setFieldValue("str_a", new StringFieldValue("a1"));

            assertNotNull(doc = Expression.execute(exp, doc));
            FieldValue val = doc.getFieldValue("out");
            assertNotNull(val);
            assertEquals(DataType.getArray(DataType.STRING), val.getDataType());
            assertTrue(val instanceof Array);
            Array arr = (Array)val;
            assertEquals(1, arr.size());
        }
        {
            Document doc = new Document(docType, "doc:scheme:");
            doc.setFieldValue("str_a", new StringFieldValue("a1,a2"));
            doc.setFieldValue("str_b", new StringFieldValue("b1"));

            assertNotNull(doc = Expression.execute(exp, doc));
            FieldValue val = doc.getFieldValue("out");
            assertNotNull(val);
            assertEquals(DataType.getArray(DataType.STRING), val.getDataType());
            assertTrue(val instanceof Array);
            Array arr = (Array)val;
            assertEquals(3, arr.size());
        }
    }
}
