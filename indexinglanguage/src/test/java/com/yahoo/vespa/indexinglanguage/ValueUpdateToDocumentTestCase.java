// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.update.ValueUpdate;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class ValueUpdateToDocumentTestCase {

    @Test
    public void requireThatIntegerFieldsAreConverted() {
        DocumentType docType = new DocumentType("my_type");
        Field field = new Field("my_int", DataType.INT);
        docType.addField(field);

        ValueUpdate update = ValueUpdate.createAssign(new IntegerFieldValue(42));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, new DocumentId("doc:foo:1"), field, update);
        assertNotNull(doc);

        assertEquals(42, ((IntegerFieldValue)doc.getFieldValue("my_int")).getInteger());
    }


    @Test
    public void requireThatClearValueUpdatesAreConverted() {
        DocumentType docType = new DocumentType("my_type");
        Field field = new Field("my_int", DataType.INT);
        docType.addField(field);

        ValueUpdate update = ValueUpdate.createClear();
        Document doc = FieldUpdateHelper.newPartialDocument(docType, new DocumentId("doc:foo:1"), field, update);
        assertNotNull(doc);

        assertNotNull(doc.getFieldValue("my_int"));
        assertEquals(new IntegerFieldValue(), doc.getFieldValue("my_int"));
    }


    @Test
    public void requireThatStringFieldsAreConverted() {
        DocumentType docType = new DocumentType("my_type");
        Field field = new Field("my_str", DataType.STRING);
        docType.addField(field);

        ValueUpdate update = ValueUpdate.createAssign(new StringFieldValue("42"));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, new DocumentId("doc:foo:1"), field, update);
        assertNotNull(doc);

        assertEquals("42", ((StringFieldValue)doc.getFieldValue("my_str")).getString());
    }

    @SuppressWarnings({ "unchecked" })
    @Test
    public void requireThatArrayFieldsAreConverted() {
        DocumentType docType = new DocumentType("my_type");
        ArrayDataType arrType = DataType.getArray(DataType.INT);
        Field field = new Field("my_arr", arrType);
        docType.addField(field);

        Array<IntegerFieldValue> arrVal = arrType.createFieldValue();
        arrVal.add(new IntegerFieldValue(6));
        arrVal.add(new IntegerFieldValue(9));
        ValueUpdate update = ValueUpdate.createAssign(arrVal);

        Document doc = FieldUpdateHelper.newPartialDocument(docType, new DocumentId("doc:foo:1"), field, update);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_arr");
        assertTrue(obj instanceof Array);
        Array arr = (Array)obj;
        assertEquals(2, arr.size());
        assertEquals(new IntegerFieldValue(6), arr.get(0));
        assertEquals(new IntegerFieldValue(9), arr.get(1));
    }

    @Test
    public void requireThatWsetKeysAreConverted() {
        DocumentType docType = new DocumentType("my_type");
        WeightedSetDataType wsetType = DataType.getWeightedSet(DataType.STRING);
        Field field = new Field("my_wset", wsetType);
        docType.addField(field);

        ValueUpdate update = ValueUpdate.createMap(new StringFieldValue("69"), ValueUpdate.createAssign(new IntegerFieldValue(96)));

        Document doc = FieldUpdateHelper.newPartialDocument(docType, new DocumentId("doc:foo:1"), field, update);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_wset");
        assertTrue(obj instanceof WeightedSet);
        WeightedSet wset = (WeightedSet)obj;
        assertEquals(1, wset.size());
        assertEquals(96, wset.get(new StringFieldValue("69")).intValue());
    }

    @Test
    public void requireThatNestedStructsAreConverted() {
        DocumentType docType = new DocumentType("my_type");
        StructDataType structType = new StructDataType("my_struct");
        structType.addField(new Field("b", DataType.INT));
        Field field = new Field("a", structType);
        docType.addField(field);

        ValueUpdate update = ValueUpdate.createMap(new StringFieldValue("b"), ValueUpdate.createAssign(new IntegerFieldValue(42)));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, new DocumentId("doc:foo:1"), field, update);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("a");
        assertTrue(obj instanceof Struct);
        Struct struct = (Struct)obj;
        assertEquals(new IntegerFieldValue(42), struct.getFieldValue("b"));
    }

    @SuppressWarnings({ "unchecked" })
    @Test
    public void requireThatAddIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        ArrayDataType arrType = DataType.getArray(DataType.INT);
        Field field = new Field("my_arr", arrType);
        docType.addField(field);

        ValueUpdate update = ValueUpdate.createMap(new IntegerFieldValue(0), ValueUpdate.createAdd(new IntegerFieldValue(6)));

        Document doc = FieldUpdateHelper.newPartialDocument(docType, new DocumentId("doc:foo:1"), field, update);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_arr");
        assertTrue(obj instanceof Array);
        Array arr = (Array)obj;
        assertEquals(1, arr.size());
        assertEquals(new IntegerFieldValue(6), arr.get(0));
    }

    @Test
    public void requireThatRemoveIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        ArrayDataType arrType = DataType.getArray(DataType.INT);
        Field field = new Field("my_arr", arrType);
        docType.addField(field);

        ValueUpdate update = ValueUpdate.createClear();

        Document doc = FieldUpdateHelper.newPartialDocument(docType, new DocumentId("doc:foo:1"), field, update);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_arr");
        assertTrue(obj instanceof Array);
        Array arr = (Array)obj;
        assertEquals(0, arr.size());
    }
}
