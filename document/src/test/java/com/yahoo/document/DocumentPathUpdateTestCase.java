// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.*;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Before;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests applying and serializing document updates.
 *
 * @author Einar M R Rosenvinge
 */
public class DocumentPathUpdateTestCase {

    DocumentTypeManager docMan;

    DocumentType docType = null;
    DocumentType docType2 = null;

    @Before
    public void setUp() {
        docMan = new DocumentTypeManager();

        docType = new DocumentType("foobar");
        docType.addField(new Field("num", DataType.INT));
        docType.addField(new Field("num2", DataType.DOUBLE));
        docType.addField(new Field("strfoo", DataType.STRING));

        DataType stringarray = DataType.getArray(DataType.STRING);
        docType.addField(new Field("strarray", stringarray));

        DataType stringwset = DataType.getWeightedSet(DataType.STRING);
        docType.addField(new Field("strwset", stringwset));

        StructDataType mystructType = new StructDataType("mystruct");
        mystructType.addField(new Field("title", DataType.STRING));
        mystructType.addField(new Field("rating", DataType.INT));

        DataType structmap = new MapDataType(DataType.STRING, mystructType);
        docType.addField(new Field("structmap", structmap));

        DataType structarray = new ArrayDataType(mystructType);
        docType.addField(new Field("structarray", structarray));

        DataType strmap = new MapDataType(DataType.STRING, DataType.STRING);
        docType.addField(new Field("strmap", strmap));

        docType.addField(new Field("struct", mystructType));

        docMan.register(docType);

        docType2 = new DocumentType("otherdoctype");
        docType2.addField(new Field("strinother", DataType.STRING));
        docMan.register(docType2);
    }

    @Test
    public void testRemoveField() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        assertNull(doc.getFieldValue("strfoo"));
        doc.setFieldValue("strfoo", "cocacola");
        assertEquals(new StringFieldValue("cocacola"), doc.getFieldValue("strfoo"));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new RemoveFieldPathUpdate(doc.getDataType(), "strfoo", null));
        docUp.applyTo(doc);
        assertNull(doc.getFieldValue("strfoo"));
    }

    @Test
    public void testApplyRemoveMultiList() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        assertNull(doc.getFieldValue("strarray"));
        Array<StringFieldValue> strArray = new Array<>(doc.getField("strarray").getDataType());
        strArray.add(new StringFieldValue("crouching tiger, hidden value"));
        strArray.add(new StringFieldValue("remove val 1"));
        strArray.add(new StringFieldValue("hello hello"));
        doc.setFieldValue("strarray", strArray);
        assertNotNull(doc.getFieldValue("strarray"));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new RemoveFieldPathUpdate(doc.getDataType(), "strarray[$x]", "foobar.strarray[$x] == \"remove val 1\""));
        docUp.applyTo(doc);
        assertEquals(2, ((List) doc.getFieldValue("strarray")).size());
        List docList = (List) doc.getFieldValue("strarray");
        assertEquals(new StringFieldValue("crouching tiger, hidden value"), docList.get(0));
        assertEquals(new StringFieldValue("hello hello"), docList.get(1));
    }

    @Test
    public void testApplyRemoveEntireListField() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        assertNull(doc.getFieldValue("strarray"));
        Array<StringFieldValue> strArray = new Array<>(doc.getField("strarray").getDataType());
        strArray.add(new StringFieldValue("this list"));
        strArray.add(new StringFieldValue("should be"));
        strArray.add(new StringFieldValue("totally removed"));
        doc.setFieldValue("strarray", strArray);
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:toast:jam"));
        docUp.addFieldPathUpdate(new RemoveFieldPathUpdate(doc.getDataType(), "strarray", null));
        docUp.applyTo(doc);
        assertNull(doc.getFieldValue("strarray"));
    }

    @Test
    public void testApplyRemoveMultiWset() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        assertNull(doc.getFieldValue("strwset"));
        WeightedSet<StringFieldValue> strwset = new WeightedSet<>(doc.getDataType().getField("strwset").getDataType());
        strwset.put(new StringFieldValue("hello hello"), 10);
        strwset.put(new StringFieldValue("remove val 1"), 20);
        doc.setFieldValue("strwset", strwset);
        assertNotNull(doc.getFieldValue("strwset"));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new RemoveFieldPathUpdate(doc.getDataType(), "strwset{remove val 1}", ""));
        docUp.applyTo(doc);
        assertEquals(1, ((WeightedSet) doc.getFieldValue("strwset")).size());
        WeightedSet docWset = (WeightedSet) doc.getFieldValue("strwset");
        assertEquals(Integer.valueOf(10), docWset.get(new StringFieldValue("hello hello")));
    }

    @Test
    public void testApplyAssignSingle() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        assertNull(doc.getFieldValue("strfoo"));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "strfoo", "", new StringFieldValue("something")));
        docUp.applyTo(doc);
        assertEquals(new StringFieldValue("something"), doc.getFieldValue("strfoo"));
    }

    @Test
    public void testApplyAssignMath() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        doc.setFieldValue(doc.getField("num"), new IntegerFieldValue(34));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "num", "", "($value * 2) / $value"));
        docUp.applyTo(doc);
        assertEquals(new IntegerFieldValue(2), doc.getFieldValue(doc.getField("num")));
    }

    @Test
    public void testDivideByZero() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        doc.setFieldValue(doc.getField("num"), new IntegerFieldValue(10));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "num", "", "100 / ($value - 10)"));
        docUp.applyTo(doc);
        assertEquals(new IntegerFieldValue(10), doc.getFieldValue(doc.getField("num")));
    }

    @Test
    public void testAssignMathFieldNotSet() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        doc.setFieldValue(doc.getField("num"), new IntegerFieldValue(10));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "num", "", "100 + foobar.num2"));
        docUp.applyTo(doc);
        assertEquals(new IntegerFieldValue(10), doc.getFieldValue(doc.getField("num")));
    }

    @Test
    public void testAssignMathMissingField() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        doc.setFieldValue(doc.getField("num"), new IntegerFieldValue(10));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "num", "", "100 + foobar.bogus"));
        docUp.applyTo(doc);
        assertEquals(new IntegerFieldValue(10), doc.getFieldValue(doc.getField("num")));
    }

    @Test
    public void testAssignMathTargetFieldNotSet() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "num", "", "100"));
        docUp.applyTo(doc);
        assertEquals(new IntegerFieldValue(100), doc.getFieldValue(doc.getField("num")));
    }

    @Test
    public void testAssignMathTargetFieldNotSetWithValue() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "num", "", "$value + 5"));
        docUp.applyTo(doc);
        assertEquals(new IntegerFieldValue(5), doc.getFieldValue(doc.getField("num")));
    }

    @Test
    public void testApplyAssignMultiList() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        assertNull(doc.getFieldValue("strarray"));
        Array<StringFieldValue> strArray = new Array<StringFieldValue>(doc.getField("strarray").getDataType());
        strArray.add(new StringFieldValue("hello hello"));
        strArray.add(new StringFieldValue("blah blah"));
        doc.setFieldValue("strarray", strArray);
        assertNotNull(doc.getFieldValue("strarray"));
        Array<StringFieldValue> array = new Array<>(doc.getField("strarray").getDataType());
        array.add(new StringFieldValue("assigned val 0"));
        array.add(new StringFieldValue("assigned val 1"));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "strarray", "", array));
        docUp.applyTo(doc);
        assertEquals(2, ((List) doc.getFieldValue("strarray")).size());
        List docList = (List) doc.getFieldValue("strarray");
        assertEquals(new StringFieldValue("assigned val 0"), docList.get(0));
        assertEquals(new StringFieldValue("assigned val 1"), docList.get(1));
    }

    @Test
    public void testApplyAssignMultiWlist() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        assertNull(doc.getFieldValue("strwset"));
        WeightedSet<StringFieldValue> strwset = new WeightedSet<>(doc.getDataType().getField("strwset").getDataType());
        strwset.put(new StringFieldValue("hello hello"), 164);
        strwset.put(new StringFieldValue("blahdi blahdi"), 243);
        doc.setFieldValue("strwset", strwset);
        assertNotNull(doc.getFieldValue("strwset"));
        WeightedSet<StringFieldValue> assignWset = new WeightedSet<>(docType.getField("strwset").getDataType());
        assignWset.put(new StringFieldValue("assigned val 0"), 5);
        assignWset.put(new StringFieldValue("assigned val 1"), 10);
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "strwset", "", assignWset));
        docUp.applyTo(doc);
        assertEquals(2, ((WeightedSet) doc.getFieldValue("strwset")).size());
        WeightedSet docWset = (WeightedSet) doc.getFieldValue("strwset");
        assertEquals(Integer.valueOf(5), docWset.get(new StringFieldValue("assigned val 0")));
        assertEquals(Integer.valueOf(10), docWset.get(new StringFieldValue("assigned val 1")));
    }

    @Test
    public void testAssignWsetRemoveIfZero() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        assertNull(doc.getFieldValue(doc.getField("strwset")));
        WeightedSet<StringFieldValue> strwset = new WeightedSet<>(doc.getDataType().getField("strwset").getDataType());
        strwset.put(new StringFieldValue("hello hello"), 164);
        strwset.put(new StringFieldValue("blahdi blahdi"), 243);
        doc.setFieldValue(doc.getField("strwset"), strwset);
        assertNotNull(doc.getFieldValue(doc.getField("strwset")));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        AssignFieldPathUpdate upd = new AssignFieldPathUpdate(doc.getDataType(), "strwset{hello hello}", "", "$value - 164");
        upd.setRemoveIfZero(true);
        docUp.addFieldPathUpdate(upd);
        docUp.applyTo(doc);
        WeightedSet docWset = (WeightedSet) doc.getFieldValue(doc.getField("strwset"));
        assertEquals(1, docWset.size());
        assertEquals(Integer.valueOf(243), docWset.get(new StringFieldValue("blahdi blahdi")));
    }

    @Test
    public void testApplyAddMultiList() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        assertNull(doc.getFieldValue("strarray"));

        Array<StringFieldValue> addList = new Array<StringFieldValue>(doc.getField("strarray").getDataType());
        addList.add(new StringFieldValue("bo"));
        addList.add(new StringFieldValue("ba"));
        addList.add(new StringFieldValue("by"));
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AddFieldPathUpdate(doc.getDataType(), "strarray", "", addList));
        docUp.applyTo(doc);
        List<StringFieldValue> values = new ArrayList<>();
        values.add(new StringFieldValue("bo"));
        values.add(new StringFieldValue("ba"));
        values.add(new StringFieldValue("by"));
        assertEquals(values, doc.getFieldValue("strarray"));
    }

    @Test
    public void testAddAndAssignList() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        assertNull(doc.getFieldValue("strarray"));

        Array strArray = new Array(doc.getField("strarray").getDataType());
        strArray.add(new StringFieldValue("hello hello"));
        strArray.add(new StringFieldValue("blah blah"));
        doc.setFieldValue("strarray", strArray);
        assertNotNull(doc.getFieldValue("strarray"));

        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "strarray[1]", "", new StringFieldValue("assigned val 1")));

        Array adds = new Array(doc.getField("strarray").getDataType());
        adds.add(new StringFieldValue("new value"));

        docUp.addFieldPathUpdate(new AddFieldPathUpdate(doc.getDataType(), "strarray", "", adds));

        docUp.applyTo(doc);
        List docList = (List) doc.getFieldValue("strarray");
        assertEquals(3, docList.size());
        assertEquals(new StringFieldValue("hello hello"), docList.get(0));
        assertEquals(new StringFieldValue("assigned val 1"), docList.get(1));
        assertEquals(new StringFieldValue("new value"), docList.get(2));
    }

    @Test
    public void testAssignSimpleMapValueWithVariable() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        MapFieldValue mfv = new MapFieldValue((MapDataType)doc.getField("strmap").getDataType());

        mfv.put(new StringFieldValue("foo"), new StringFieldValue("bar"));
        mfv.put(new StringFieldValue("baz"), new StringFieldValue("bananas"));
        doc.setFieldValue("strmap", mfv);

        // Select on map value, not key
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:hargl:bargl"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "strmap{$x}",
                "foobar.strmap{$x} == \"bar\"", new StringFieldValue("shinyvalue")));
        docUp.applyTo(doc);

        MapFieldValue valueNow = (MapFieldValue)doc.getFieldValue("strmap");
        assertEquals(2, valueNow.size());
        assertEquals(new StringFieldValue("shinyvalue"), valueNow.get(new StringFieldValue("foo")));
        assertEquals(new StringFieldValue("bananas"), valueNow.get(new StringFieldValue("baz")));
    }

    @Test
    public void testKeyParsing() {
        assertEquals(new FieldPathEntry.KeyParseResult("", 2), FieldPathEntry.parseKey("{}"));
        assertEquals(new FieldPathEntry.KeyParseResult("abc", 5), FieldPathEntry.parseKey("{abc}"));
        assertEquals(new FieldPathEntry.KeyParseResult("abc", 8), FieldPathEntry.parseKey("{   abc}"));
        // TODO: post-skipping of spaces not supported for verbatim keys in C++. support here?
        //assertEquals(new FieldPathEntry.KeyParseResult("abc", 8), FieldPathEntry.parseKey("{abc   }"));
        assertEquals(new FieldPathEntry.KeyParseResult("hello", 9), FieldPathEntry.parseKey("{\"hello\"}"));
        assertEquals(new FieldPathEntry.KeyParseResult("{abc}", 9), FieldPathEntry.parseKey("{\"{abc}\"}"));
        assertEquals(new FieldPathEntry.KeyParseResult("abc", 5), FieldPathEntry.parseKey("{abc}stuff"));
        assertEquals(new FieldPathEntry.KeyParseResult("abc", 10), FieldPathEntry.parseKey("{   \"abc\"}"));
        assertEquals(new FieldPathEntry.KeyParseResult("abc", 10), FieldPathEntry.parseKey("{\"abc\"   }"));
        assertEquals(new FieldPathEntry.KeyParseResult("abc", 13), FieldPathEntry.parseKey("{   \"abc\"   }"));
        // Test quote escaping
        assertEquals(new FieldPathEntry.KeyParseResult("\"doom house\"", 18), FieldPathEntry.parseKey("{\"\\\"doom house\\\"\"}"));
        assertEquals(new FieldPathEntry.KeyParseResult("\"", 6), FieldPathEntry.parseKey("{\"\\\"\"}"));
        assertEquals(new FieldPathEntry.KeyParseResult("a\"b\"c", 11), FieldPathEntry.parseKey("{\"a\\\"b\\\"c\"}"));
        // Test failure conditions
        try {
            FieldPathEntry.parseKey("");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Key '' does not start with '{'", e.getMessage());
        }

        try {
            FieldPathEntry.parseKey("{");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Key '{' is incomplete. No matching '}'", e.getMessage());
        }

        try {
            FieldPathEntry.parseKey("{aaa");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Key '{aaa' is incomplete. No matching '}'", e.getMessage());
        }

        try {
            FieldPathEntry.parseKey("{\"things}");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Escaped key '{\"things}' is incomplete. No matching '\"'", e.getMessage());
        }

        try {
            FieldPathEntry.parseKey("{\"things\\}");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Escaped key '{\"things\\}' has bad quote character escape sequence. Expected '\"'", e.getMessage());
        }
    }

    @Test
    public void testKeyWithEscapedChars() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        MapFieldValue mfv = new MapFieldValue((MapDataType)doc.getField("strmap").getDataType());

        mfv.put(new StringFieldValue("here is a \"fancy\" :-} map key :-{"), new StringFieldValue("bar"));
        mfv.put(new StringFieldValue("baz"), new StringFieldValue("bananas"));
        doc.setFieldValue("strmap", mfv);

        // Select on map value, not key
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:hargl:bargl"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "strmap{\"here is a \\\"fancy\\\" :-} map key :-{\"}",
                "", new StringFieldValue("shinyvalue")));
        docUp.applyTo(doc);

        MapFieldValue valueNow = (MapFieldValue)doc.getFieldValue("strmap");
        assertEquals(2, valueNow.size());
        assertEquals(new StringFieldValue("shinyvalue"), valueNow.get(new StringFieldValue("here is a \"fancy\" :-} map key :-{")));
        assertEquals(new StringFieldValue("bananas"), valueNow.get(new StringFieldValue("baz")));
    }

    @Test
    public void testAssignMap() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        MapFieldValue mfv = new MapFieldValue((MapDataType)doc.getField("structmap").getDataType());
        Struct fv1 = new Struct(mfv.getDataType().getValueType());
        fv1.setFieldValue("title", new StringFieldValue("thomas"));
        fv1.setFieldValue("rating", new IntegerFieldValue(32));

        mfv.put(new StringFieldValue("foo"), fv1);

        Struct fv2 = new Struct(mfv.getDataType().getValueType());
        fv2.setFieldValue("title", new StringFieldValue("cyril"));
        fv2.setFieldValue("rating", new IntegerFieldValue(16));

        mfv.put(new StringFieldValue("bar"), fv2);

        Struct fv3 = new Struct(mfv.getDataType().getValueType());
        fv3.setFieldValue("title", new StringFieldValue("ulf"));
        fv3.setFieldValue("rating", new IntegerFieldValue(8));

        mfv.put(new StringFieldValue("zoo"), fv3);

        doc.setFieldValue("structmap", mfv);

        Struct fv4 = new Struct(mfv.getDataType().getValueType());
        fv4.setFieldValue("title", new StringFieldValue("tor brede"));
        fv4.setFieldValue("rating", new IntegerFieldValue(48));

        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "structmap{bar}", "", fv4));
        docUp.applyTo(doc);

        MapFieldValue valueNow = (MapFieldValue)doc.getFieldValue("structmap");
        assertEquals(fv1, valueNow.get(new StringFieldValue("foo")));
        assertEquals(fv4, valueNow.get(new StringFieldValue("bar")));
        assertEquals(fv3, valueNow.get(new StringFieldValue("zoo")));
    }

    @Test
    public void testAssignMapStruct() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        MapFieldValue mfv = new MapFieldValue((MapDataType)doc.getField("structmap").getDataType());
        Struct fv1 = new Struct(mfv.getDataType().getValueType());
        fv1.setFieldValue("title", new StringFieldValue("thomas"));
        fv1.setFieldValue("rating", new IntegerFieldValue(32));

        mfv.put(new StringFieldValue("foo"), fv1);

        Struct fv2 = new Struct(mfv.getDataType().getValueType());
        fv2.setFieldValue("title", new StringFieldValue("cyril"));
        fv2.setFieldValue("rating", new IntegerFieldValue(16));

        mfv.put(new StringFieldValue("bar"), fv2);

        Struct fv3 = new Struct(mfv.getDataType().getValueType());
        fv3.setFieldValue("title", new StringFieldValue("ulf"));
        fv3.setFieldValue("rating", new IntegerFieldValue(8));

        mfv.put(new StringFieldValue("zoo"), fv3);

        doc.setFieldValue("structmap", mfv);

        Struct fv4 = new Struct(mfv.getDataType().getValueType());
        fv4.setFieldValue("title", new StringFieldValue("cyril"));
        fv4.setFieldValue("rating", new IntegerFieldValue(48));

        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "structmap{bar}.rating", "", new IntegerFieldValue(48)));
        docUp.applyTo(doc);

        MapFieldValue valueNow = (MapFieldValue)doc.getFieldValue("structmap");
        assertEquals(fv1, valueNow.get(new StringFieldValue("foo")));
        assertEquals(fv4, valueNow.get(new StringFieldValue("bar")));
        assertEquals(fv3, valueNow.get(new StringFieldValue("zoo")));
    }

    @Test
    public void testAssignMapStructVariable() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        MapFieldValue mfv = new MapFieldValue((MapDataType)doc.getField("structmap").getDataType());
        Struct fv1 = new Struct(mfv.getDataType().getValueType());
        fv1.setFieldValue(fv1.getField("title"), new StringFieldValue("thomas"));
        fv1.setFieldValue(fv1.getField("rating"), new IntegerFieldValue(32));

        mfv.put(new StringFieldValue("foo"), fv1);

        Struct fv2 = new Struct(mfv.getDataType().getValueType());
        fv2.setFieldValue(fv2.getField("title"), new StringFieldValue("cyril"));
        fv2.setFieldValue(fv2.getField("rating"), new IntegerFieldValue(16));

        mfv.put(new StringFieldValue("bar"), fv2);

        Struct fv3 = new Struct(mfv.getDataType().getValueType());
        fv3.setFieldValue(fv3.getField("title"), new StringFieldValue("ulf"));
        fv3.setFieldValue(fv3.getField("rating"), new IntegerFieldValue(8));

        mfv.put(new StringFieldValue("zoo"), fv3);

        doc.setFieldValue(doc.getField("structmap"), mfv);

        Struct fv4 = new Struct(mfv.getDataType().getValueType());
        fv4.setFieldValue(fv4.getField("title"), new StringFieldValue("cyril"));
        fv4.setFieldValue(fv4.getField("rating"), new IntegerFieldValue(48));

        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "structmap{$x}.rating", "foobar.structmap{$x}.title == \"cyril\"", new IntegerFieldValue(48)));
        docUp.applyTo(doc);

        MapFieldValue valueNow = (MapFieldValue)doc.getFieldValue("structmap");
        assertEquals(fv1, valueNow.get(new StringFieldValue("foo")));
        assertEquals(fv4, valueNow.get(new StringFieldValue("bar")));
        assertEquals(fv3, valueNow.get(new StringFieldValue("zoo")));
    }

    @Test
    public void testAssignMapNoexist() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        MapFieldValue mfv = new MapFieldValue((MapDataType)doc.getField("structmap").getDataType());

        Struct fv1 = new Struct(mfv.getDataType().getValueType());
        fv1.setFieldValue("title", new StringFieldValue("thomas"));
        fv1.setFieldValue("rating", new IntegerFieldValue(32));

        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        docUp.addFieldPathUpdate(new AssignFieldPathUpdate(doc.getDataType(), "structmap{foo}", "", fv1));
        docUp.applyTo(doc);

        MapFieldValue valueNow = (MapFieldValue)doc.getFieldValue("structmap");
        assertEquals(fv1, valueNow.get(new StringFieldValue("foo")));
    }

    @Test
    public void testAssignMapNoexistNocreate() {
        Document doc = new Document(docMan.getDocumentType("foobar"), new DocumentId("doc:something:foooo"));
        MapFieldValue mfv = new MapFieldValue((MapDataType)doc.getField("structmap").getDataType());

        Struct fv1 = new Struct(mfv.getDataType().getValueType());
        fv1.setFieldValue("title", new StringFieldValue("thomas"));
        fv1.setFieldValue("rating", new IntegerFieldValue(32));

        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        AssignFieldPathUpdate ass = new AssignFieldPathUpdate(doc.getDataType(), "structmap{foo}", "", fv1);
        ass.setCreateMissingPath(false);
        docUp.addFieldPathUpdate(ass);
        docUp.applyTo(doc);

        MapFieldValue valueNow = (MapFieldValue)doc.getFieldValue("structmap");
        assertNull(valueNow);
    }

    @Test
    public void testAssignSerialization() {
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        AssignFieldPathUpdate ass = new AssignFieldPathUpdate(docType, "num", "", "3");
        ass.setCreateMissingPath(false);
        docUp.addFieldPathUpdate(ass);

        GrowableByteBuffer buffer = new GrowableByteBuffer();
        docUp.serialize(DocumentSerializerFactory.createHead(buffer));
        buffer.flip();
        DocumentUpdate docUp2 = new DocumentUpdate(DocumentDeserializerFactory.createHead(docMan, buffer));

        assertEquals(docUp, docUp2);
    }

    @Test
    public void testAddSerialization() {
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        Array strArray = new Array(docType.getField("strarray").getDataType());
        strArray.add(new StringFieldValue("hello hello"));
        strArray.add(new StringFieldValue("blah blah"));

        AddFieldPathUpdate add = new AddFieldPathUpdate(docType, "strarray", "", strArray);
        docUp.addFieldPathUpdate(add);

        GrowableByteBuffer buffer = new GrowableByteBuffer();
        docUp.serialize(DocumentSerializerFactory.createHead(buffer));
        buffer.flip();
        DocumentUpdate docUp2 = new DocumentUpdate(DocumentDeserializerFactory.createHead(docMan, buffer));

        assertEquals(docUp, docUp2);
    }

    @Test
    public void testRemoveSerialization() {
        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:foo:bar"));
        RemoveFieldPathUpdate remove = new RemoveFieldPathUpdate(docType, "num", "foobar.num > 0");
        docUp.addFieldPathUpdate(remove);

        GrowableByteBuffer buffer = new GrowableByteBuffer();
        docUp.serialize(DocumentSerializerFactory.createHead(buffer));
        buffer.flip();
        DocumentUpdate docUp2 = new DocumentUpdate(DocumentDeserializerFactory.createHead(docMan, buffer));

        assertEquals(docUp, docUp2);
    }

    @Test
    public void testStartsWith() {
        FieldPath fp1 = docType.buildFieldPath("struct");
        FieldPath fp2 = docType.buildFieldPath("struct.title");
        assertTrue(fp2.startsWith(fp1));
        assertTrue(fp2.startsWith(fp2));
        assertFalse(fp1.startsWith(fp2));
    }

    private DocumentUpdate createDocumentUpdateForSerialization() {
        docMan = DocumentTestCase.setUpCppDocType();
        docType = docMan.getDocumentType("serializetest");

        DocumentUpdate docUp = new DocumentUpdate(docType, new DocumentId("doc:serialization:xlanguage"));

        AssignFieldPathUpdate ass = new AssignFieldPathUpdate(docType, "intfield", "", "3");
        ass.setCreateMissingPath(false);
        ass.setRemoveIfZero(true);
        docUp.addFieldPathUpdate(ass);

        Array fArray = new Array(docType.getField("arrayoffloatfield").getDataType());
        fArray.add(new FloatFieldValue(12.0f));
        fArray.add(new FloatFieldValue(5.0f));

        AddFieldPathUpdate add = new AddFieldPathUpdate(docType, "arrayoffloatfield", "", fArray);
        docUp.addFieldPathUpdate(add);

        RemoveFieldPathUpdate remove = new RemoveFieldPathUpdate(docType, "intfield", "serializetest.intfield > 0");
        docUp.addFieldPathUpdate(remove);

        return docUp;
    }

    @Test
    public void testGenerateSerializedFile() throws IOException {
        DocumentUpdate docUp = createDocumentUpdateForSerialization();

        GrowableByteBuffer buffer = new GrowableByteBuffer();
        docUp.serialize(DocumentSerializerFactory.createHead(buffer));

        int size = buffer.position();
        buffer.position(0);

        FileOutputStream fos = new FileOutputStream("src/tests/data/serialize-fieldpathupdate-java.dat");
        fos.write(buffer.array(), 0, size);
        fos.close();
    }

    @Test
    public void testReadSerializedFile() throws IOException {
        docMan = DocumentTestCase.setUpCppDocType();
        byte[] data = DocumentTestCase.readFile("src/tests/data/serialize-fieldpathupdate-cpp.dat");
        DocumentDeserializer buf = DocumentDeserializerFactory.createHead(docMan, GrowableByteBuffer.wrap(data));

        DocumentUpdate upd = new DocumentUpdate(buf);

        DocumentUpdate compare = createDocumentUpdateForSerialization();

        assertEquals(compare, upd);
    }

}
