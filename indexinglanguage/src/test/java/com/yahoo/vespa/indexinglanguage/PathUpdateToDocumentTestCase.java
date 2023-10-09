// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class PathUpdateToDocumentTestCase {

    @Test
    public void requireThatIntegerFieldsAreConverted() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField(new Field("my_int", DataType.INT));

        FieldPathUpdate upd = new AssignFieldPathUpdate(docType, "my_int", "", new IntegerFieldValue(69));
        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
        assertNotNull(doc);

        assertEquals(69, ((IntegerFieldValue)doc.getFieldValue("my_int")).getInteger());
    }

    @Test
    public void requireThatStringFieldsAreConverted() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField(new Field("my_str", DataType.STRING));

        FieldPathUpdate upd = new AssignFieldPathUpdate(docType, "my_str", "", new StringFieldValue("69"));
        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
        assertNotNull(doc);

        assertEquals("69", ((StringFieldValue)doc.getFieldValue("my_str")).getString());
    }

    @SuppressWarnings({ "unchecked" })
    @Test
    public void requireThatArrayFieldsAreConverted() {
        DocumentType docType = new DocumentType("my_type");
        ArrayDataType arrType = DataType.getArray(DataType.INT);
        docType.addField(new Field("my_arr", arrType));

        Array<IntegerFieldValue> arrVal = arrType.createFieldValue();
        arrVal.add(new IntegerFieldValue(6));
        arrVal.add(new IntegerFieldValue(9));
        FieldPathUpdate upd = new AssignFieldPathUpdate(docType, "my_arr", "", arrVal);

        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
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
        docType.addField(new Field("my_wset", wsetType));

        FieldPathUpdate upd = new AssignFieldPathUpdate(docType, "my_wset{69}", "", new IntegerFieldValue(96));

        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
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
        docType.addField(new Field("a", structType));

        FieldPathUpdate upd = new AssignFieldPathUpdate(docType, "a.b", "", new IntegerFieldValue(69));

        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("a");
        assertTrue(obj instanceof Struct);
        Struct struct = (Struct)obj;
        assertEquals(new IntegerFieldValue(69), struct.getFieldValue("b"));
    }

    @SuppressWarnings({ "unchecked" })
    @Test
    public void requireThatAddIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        ArrayDataType arrType = DataType.getArray(DataType.INT);
        docType.addField(new Field("my_arr", arrType));

        Array<IntegerFieldValue> arrVal = arrType.createFieldValue();
        arrVal.add(new IntegerFieldValue(6));
        arrVal.add(new IntegerFieldValue(9));
        FieldPathUpdate upd = new AddFieldPathUpdate(docType, "my_arr", "", arrVal);

        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_arr");
        assertTrue(obj instanceof Array);
        Array arr = (Array)obj;
        assertEquals(2, arr.size());
        assertEquals(new IntegerFieldValue(6), arr.get(0));
        assertEquals(new IntegerFieldValue(9), arr.get(1));
    }

    @Test
    public void requireThatRemoveIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        ArrayDataType arrType = DataType.getArray(DataType.INT);
        docType.addField(new Field("my_arr", arrType));

        FieldPathUpdate upd = new RemoveFieldPathUpdate(docType, "my_arr", "");

        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_arr");
        assertTrue(obj instanceof Array);
        Array arr = (Array)obj;
        assertEquals(0, arr.size());
    }
}
