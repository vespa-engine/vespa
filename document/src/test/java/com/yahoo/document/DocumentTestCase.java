// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.compress.CompressionType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.vespa.objects.BufferSerializer;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test for Document and all its features, including (de)serialization.
 *
 * @author <a href="thomasg@yahoo-inc.com>Thomas Gundersen</a>
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class DocumentTestCase extends DocumentTestCaseBase {

    private static final String SERTEST_DOC_AS_XML_HEAD =
            "<document documenttype=\"sertest\" documentid=\"doc:sertest:foobar\">\n" +
            " <mailid>emailfromalicetobob&amp;someone</mailid>\n" +
            " <date>-2013512400</date>\n" +
            " <attachmentcount>2</attachmentcount>\n" +
            " <rawfield binaryencoding=\"base64\">AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYGFiYw==</rawfield>\n";

    private static final String SERTEST_DOC_AS_XML_WEIGHT1 =
            " <weightedfield>\n" +
            "  <item weight=\"10\">this is another test, blah blah</item>\n" +
            "  <item weight=\"5\">this is a test</item>\n" +
            " </weightedfield>\n";

    private static final String SERTEST_DOC_AS_XML_WEIGHT2 =
            " <weightedfield>\n" +
            "  <item weight=\"5\">this is a test</item>\n" +
            "  <item weight=\"10\">this is another test, blah blah</item>\n" +
            " </weightedfield>\n";

    private static final String SERTEST_DOC_AS_XML_SUNNYVALE =
            " <myposfield>N37.374821;W122.057174</myposfield>\n";

    private static final String SERTEST_DOC_AS_XML_FOOT =
            " <docindoc documenttype=\"docindoc\" documentid=\"doc:sertest:inserted\">\n" +
            "  <tull>ball</tull>\n" +
            " </docindoc>\n" +
            " <mapfield>\n" +
            "  <item>\n" +
            "   <key>foo2</key>\n" +
            "   <value>bar2</value>\n" +
            "  </item>\n" +
            "  <item>\n" +
            "   <key>foo1</key>\n" +
            "   <value>bar1</value>\n" +
            "  </item>\n" +
            " </mapfield>\n" +
            SERTEST_DOC_AS_XML_SUNNYVALE +
            "</document>\n";

    static DocumentTypeManager setUpCppDocType() {
        return setUpDocType("file:src/tests/data/crossplatform-java-cpp-document.cfg");
    }

    static DocumentTypeManager setUpDocType(String filename) {
        DocumentTypeManager dcMan = new DocumentTypeManager();
        DocumentTypeManagerConfigurer.configure(dcMan, filename);
        return dcMan;
    }

    public void setUpSertestDocType() {
        docMan = new DocumentTypeManager();

        DocumentType docInDocType = new DocumentType("docindoc");
        docInDocType.addField(new Field("tull", 2, docMan.getDataType(2), true));

        docMan.registerDocumentType(docInDocType);

        DocumentType sertestDocType = new DocumentType("sertest");
        sertestDocType.addField(new Field("mailid", 2, docMan.getDataType(2), true));
        sertestDocType.addField(new Field("date", 3, docMan.getDataType(0), true));
        sertestDocType.addField(new Field("from", 4, docMan.getDataType(2), true));
        sertestDocType.addField(new Field("to", 6, docMan.getDataType(2), true));
        sertestDocType.addField(new Field("subject", 9, docMan.getDataType(2), true));
        sertestDocType.addField(new Field("body", 10, docMan.getDataType(2), false));
        sertestDocType.addField(new Field("attachmentcount", 11, docMan.getDataType(0), false));
        sertestDocType.addField(new Field("attachments", 1081629685, DataType.getArray(docMan.getDataType(2)), false));
        sertestDocType.addField(new Field("rawfield", 879, DataType.RAW, false));
        sertestDocType.addField(new Field("weightedfield", 880, DataType.getWeightedSet(DataType.STRING), false));
        sertestDocType.addField(new Field("weightedfieldCreate", 881, DataType.getWeightedSet(DataType.STRING, true, false), false));
        sertestDocType.addField(new Field("docindoc", 882, docInDocType, false));
        sertestDocType.addField(new Field("mapfield", 883, new MapDataType(DataType.STRING, DataType.STRING), false));
        sertestDocType.addField(new Field("myposfield", 884, PositionDataType.INSTANCE, false));

        docMan.registerDocumentType(sertestDocType);
    }

    static byte[] readFile(String filename) throws IOException {
        FileInputStream fis = new FileInputStream(filename);
        byte[] data = new byte[1000];
        int tot = fis.read(data);
        if (tot == -1) {
            throw new IOException("Could not read from file " + filename);
        }

        return data;
    }

    private Document getSertestDocument() {
        Document doc = new Document(docMan.getDocumentType("sertest"), new DocumentId("doc:sertest:foobar"));
        doc.setFieldValue("mailid", "emailfromalicetobob");
        doc.setFieldValue("date", -2013512400); // 03/13/06 11:00:00
        doc.setFieldValue("attachmentcount", 2);

        byte[] rawBytes = new byte[100];
        for (int i = 0; i < rawBytes.length; i++) {
            rawBytes[i] = (byte)i;
        }

        doc.setFieldValue("rawfield", new Raw(ByteBuffer.wrap(rawBytes)));

        Document docInDoc = new Document(docMan.getDocumentType("docindoc"), new DocumentId("doc:sertest:inserted"));
        docInDoc.setFieldValue("tull", "ball");
        doc.setFieldValue("docindoc", docInDoc);

        WeightedSet<StringFieldValue> wset = new WeightedSet<>(DataType.getWeightedSet(DataType.STRING));
        wset.put(new StringFieldValue("this is a test"), 5);
        wset.put(new StringFieldValue("this is another test, blah blah"), 10);
        doc.setFieldValue("weightedfield", wset);

        MapFieldValue<StringFieldValue, StringFieldValue> map = new MapFieldValue<>(new MapDataType(DataType.STRING, DataType.STRING));
        map.put(new StringFieldValue("foo1"), new StringFieldValue("bar1"));
        map.put(new StringFieldValue("foo2"), new StringFieldValue("bar2"));
        doc.setFieldValue("mapfield", map);

        return doc;
    }

    @Test
    public void testTypeChecking() {
        DocumentType type = new DocumentType("test");
        type.addField(new Field("double", DataType.DOUBLE));
        type.addField(new Field("float", DataType.FLOAT));
        type.addField(new Field("int", DataType.INT));
        type.addField(new Field("long", DataType.LONG));
        type.addField(new Field("string", DataType.STRING));

        Document doc = new Document(type, "doc:scheme:");
        FieldValue stringVal = new StringFieldValue("69");
        FieldValue doubleVal = new DoubleFieldValue(6.9);
        FieldValue floatVal = new FloatFieldValue(6.9f);
        FieldValue intVal = new IntegerFieldValue(69);
        FieldValue longVal = new LongFieldValue(69L);

        doc.setFieldValue("string", stringVal);
        doc.setFieldValue("string", doubleVal);
        doc.setFieldValue("string", floatVal);
        doc.setFieldValue("string", intVal);
        doc.setFieldValue("string", longVal);

        doc.setFieldValue("double", stringVal);
        doc.setFieldValue("double", doubleVal);
        doc.setFieldValue("double", floatVal);
        doc.setFieldValue("double", intVal);
        doc.setFieldValue("double", longVal);

        doc.setFieldValue("float", stringVal);
        doc.setFieldValue("float", doubleVal);
        doc.setFieldValue("float", floatVal);
        doc.setFieldValue("float", intVal);
        doc.setFieldValue("float", longVal);

        doc.setFieldValue("int", stringVal);
        doc.setFieldValue("int", doubleVal);
        doc.setFieldValue("int", floatVal);
        doc.setFieldValue("int", intVal);
        doc.setFieldValue("int", longVal);

        doc.setFieldValue("long", stringVal);
        doc.setFieldValue("long", doubleVal);
        doc.setFieldValue("long", floatVal);
        doc.setFieldValue("long", intVal);
        doc.setFieldValue("long", longVal);
    }

    class VariableIteratorHandler extends FieldPathIteratorHandler {

        public String retVal = "";

        @Override
        public void onPrimitive(FieldValue fv) {

            for (Map.Entry<String, IndexValue> entry : getVariables().entrySet()) {
                retVal += entry.getKey() + ": " + entry.getValue() + ",";
            }
            retVal += " - " + fv + "\n";
        }
    }

    @Test
    public void testVariables() {
        ArrayDataType iarr = new ArrayDataType(DataType.INT);
        ArrayDataType iiarr = new ArrayDataType(iarr);
        ArrayDataType iiiarr = new ArrayDataType(iiarr);

        DocumentType type = new DocumentType("test");
        type.addField(new Field("iiiarray", iiiarr));

        Array<Array<Array<IntegerFieldValue>>> iiiaV = new Array<>(iiiarr);
        for (int i = 1; i < 4; i++) {
            Array<Array<IntegerFieldValue>> iiaV = new Array<>(iiarr);
            for (int j = 1; j < 4; j++) {
                Array<IntegerFieldValue> iaV = new Array<>(iarr);
                for (int k = 1; k < 4; k++) {
                    iaV.add(new IntegerFieldValue(i * j * k));
                }
                iiaV.add(iaV);
            }
            iiiaV.add(iiaV);
        }

        Document doc = new Document(type, new DocumentId("doc:foo:testdoc"));
        doc.setFieldValue("iiiarray", iiiaV);

        {
            VariableIteratorHandler handler = new VariableIteratorHandler();
            FieldPath path = type.buildFieldPath("iiiarray[$x][$y][$z]");
            doc.iterateNested(path, 0, handler);

            String fasit =
                    "x: 0,y: 0,z: 0, - 1\n" +
                    "x: 0,y: 0,z: 1, - 2\n" +
                    "x: 0,y: 0,z: 2, - 3\n" +
                    "x: 0,y: 1,z: 0, - 2\n" +
                    "x: 0,y: 1,z: 1, - 4\n" +
                    "x: 0,y: 1,z: 2, - 6\n" +
                    "x: 0,y: 2,z: 0, - 3\n" +
                    "x: 0,y: 2,z: 1, - 6\n" +
                    "x: 0,y: 2,z: 2, - 9\n" +
                    "x: 1,y: 0,z: 0, - 2\n" +
                    "x: 1,y: 0,z: 1, - 4\n" +
                    "x: 1,y: 0,z: 2, - 6\n" +
                    "x: 1,y: 1,z: 0, - 4\n" +
                    "x: 1,y: 1,z: 1, - 8\n" +
                    "x: 1,y: 1,z: 2, - 12\n" +
                    "x: 1,y: 2,z: 0, - 6\n" +
                    "x: 1,y: 2,z: 1, - 12\n" +
                    "x: 1,y: 2,z: 2, - 18\n" +
                    "x: 2,y: 0,z: 0, - 3\n" +
                    "x: 2,y: 0,z: 1, - 6\n" +
                    "x: 2,y: 0,z: 2, - 9\n" +
                    "x: 2,y: 1,z: 0, - 6\n" +
                    "x: 2,y: 1,z: 1, - 12\n" +
                    "x: 2,y: 1,z: 2, - 18\n" +
                    "x: 2,y: 2,z: 0, - 9\n" +
                    "x: 2,y: 2,z: 1, - 18\n" +
                    "x: 2,y: 2,z: 2, - 27\n";

            assertEquals(fasit, handler.retVal);
        }
    }

    @Test
    public void testGetRecursiveValue() {
        Document doc = new Document(testDocType, new DocumentId("doc:ns:testdoc"));
        doc.setFieldValue("primitive1", 1);

        Struct l1s1 = new Struct(doc.getField("l1s1").getDataType());
        l1s1.setFieldValue("primitive1", 2);

        Struct l2s1 = new Struct(doc.getField("struct2").getDataType());
        l2s1.setFieldValue("primitive1", 3);
        l2s1.setFieldValue("primitive2", 4);

        Array<IntegerFieldValue> iarr1 = new Array<>(l2s1.getField("iarray").getDataType());
        iarr1.add(new IntegerFieldValue(11));
        iarr1.add(new IntegerFieldValue(12));
        iarr1.add(new IntegerFieldValue(13));
        l2s1.setFieldValue("iarray", iarr1);

        ArrayDataType dt = (ArrayDataType)l2s1.getField("sarray").getDataType();
        Array<Struct> sarr1 = new Array<>(dt);
        {
            Struct l3s1 = new Struct(dt.getNestedType());
            l3s1.setFieldValue("primitive1", 1);
            l3s1.setFieldValue("primitive2", 2);
            sarr1.add(l3s1);
        }
        {
            Struct l3s1 = new Struct(dt.getNestedType());
            l3s1.setFieldValue("primitive1", 1);
            l3s1.setFieldValue("primitive2", 2);
            sarr1.add(l3s1);
        }
        l2s1.setFieldValue("sarray", sarr1);

        MapFieldValue<StringFieldValue, StringFieldValue> smap1 = new MapFieldValue<>((MapDataType)l2s1.getField("smap").getDataType());
        smap1.put(new StringFieldValue("leonardo"), new StringFieldValue("dicaprio"));
        smap1.put(new StringFieldValue("ellen"), new StringFieldValue("page"));
        smap1.put(new StringFieldValue("joseph"), new StringFieldValue("gordon-levitt"));
        l2s1.setFieldValue("smap", smap1);

        l1s1.setFieldValue("ss", l2s1.clone());

        MapFieldValue<StringFieldValue, Struct> structmap1 = new MapFieldValue<>((MapDataType)l1s1.getField("structmap").getDataType());
        structmap1.put(new StringFieldValue("test"), l2s1.clone());
        l1s1.setFieldValue("structmap", structmap1);

        WeightedSet<StringFieldValue> wset1 = new WeightedSet<>(l1s1.getField("wset").getDataType());
        wset1.add(new StringFieldValue("foo"));
        wset1.add(new StringFieldValue("bar"));
        wset1.add(new StringFieldValue("zoo"));
        l1s1.setFieldValue("wset", wset1);

        Struct l2s2 = new Struct(doc.getField("struct2").getDataType());
        l2s2.setFieldValue("primitive1", 5);
        l2s2.setFieldValue("primitive2", 6);

        WeightedSet<Struct> wset2 = new WeightedSet<>(l1s1.getField("structwset").getDataType());
        wset2.add(l2s1.clone());
        wset2.add(l2s2.clone());
        l1s1.setFieldValue("structwset", wset2);

        doc.setFieldValue("l1s1", l1s1.clone());

        {
            FieldValue fv = doc.getRecursiveValue("l1s1");
            assertEquals(l1s1, fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.primitive1");
            assertEquals(new IntegerFieldValue(2), fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.ss");
            assertEquals(l2s1, fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.ss.iarray");
            assertEquals(iarr1, fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.ss.iarray[2]");
            assertEquals(new IntegerFieldValue(13), fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.ss.iarray[3]");
            assertNull(fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.ss.sarray[0].primitive1");
            assertEquals(new IntegerFieldValue(1), fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.ss.smap{joseph}");
            assertEquals(new StringFieldValue("gordon-levitt"), fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.ss.smap.key");
            assertEquals(3, ((Array)fv).size());
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.structmap{test}.primitive1");
            assertEquals(new IntegerFieldValue(3), fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.structmap.value.primitive1");
            assertEquals(new IntegerFieldValue(3), fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.wset{foo}");
            assertEquals(new IntegerFieldValue(1), fv);
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.wset.key");
            assertEquals(3, ((Array)fv).size());
        }

        {
            FieldValue fv = doc.getRecursiveValue("l1s1.structwset.key.primitive1");
            assertEquals(DataType.INT, (((ArrayDataType)fv.getDataType()).getNestedType()));
            assertEquals(2, ((Array)fv).size());
        }
    }

    class ModifyIteratorHandler extends FieldPathIteratorHandler {

        public ModificationStatus doModify(FieldValue fv) {
            if (fv instanceof StringFieldValue) {
                fv.assign("newvalue");
                return ModificationStatus.MODIFIED;
            }

            return ModificationStatus.NOT_MODIFIED;
        }

        public boolean onComplex(FieldValue fv) {
            return false;
        }
    }

    class AddIteratorHandler extends FieldPathIteratorHandler {

        @SuppressWarnings("unchecked")
        public ModificationStatus doModify(FieldValue fv) {
            if (fv instanceof Array) {
                ((Array)fv).add(new IntegerFieldValue(32));
                return ModificationStatus.MODIFIED;
            }

            return ModificationStatus.NOT_MODIFIED;
        }

        public boolean onComplex(FieldValue fv) {
            return false;
        }
    }

    class RemoveIteratorHandler extends FieldPathIteratorHandler {

        public ModificationStatus doModify(FieldValue fv) {
            return ModificationStatus.REMOVED;
        }

        public boolean onComplex(FieldValue fv) {
            return false;
        }
    }

    @Test
    public void testModifyDocument() {
        Document doc = new Document(testDocType, new DocumentId("doc:ns:testdoc"));
        doc.setFieldValue("primitive1", 1);

        Struct l1s1 = new Struct(doc.getField("l1s1").getDataType());
        l1s1.setFieldValue("primitive1", 2);

        Struct l2s1 = new Struct(doc.getField("struct2").getDataType());
        l2s1.setFieldValue("primitive1", 3);
        l2s1.setFieldValue("primitive2", 4);

        Array<IntegerFieldValue> iarr1 = new Array<>(l2s1.getField("iarray").getDataType());
        iarr1.add(new IntegerFieldValue(11));
        iarr1.add(new IntegerFieldValue(12));
        iarr1.add(new IntegerFieldValue(13));
        l2s1.setFieldValue("iarray", iarr1);

        ArrayDataType dt = (ArrayDataType)l2s1.getField("sarray").getDataType();
        Array<Struct> sarr1 = new Array<>(dt);
        {
            Struct l3s1 = new Struct(dt.getNestedType());
            l3s1.setFieldValue("primitive1", 1);
            l3s1.setFieldValue("primitive2", 2);
            sarr1.add(l3s1);
        }
        {
            Struct l3s1 = new Struct(dt.getNestedType());
            l3s1.setFieldValue("primitive1", 1);
            l3s1.setFieldValue("primitive2", 2);
            sarr1.add(l3s1);
        }
        l2s1.setFieldValue("sarray", sarr1);

        MapFieldValue<StringFieldValue, StringFieldValue> smap1 = new MapFieldValue<>((MapDataType)l2s1.getField("smap").getDataType());
        smap1.put(new StringFieldValue("leonardo"), new StringFieldValue("dicaprio"));
        smap1.put(new StringFieldValue("ellen"), new StringFieldValue("page"));
        smap1.put(new StringFieldValue("joseph"), new StringFieldValue("gordon-levitt"));
        l2s1.setFieldValue("smap", smap1);

        l1s1.setFieldValue("ss", l2s1.clone());

        MapFieldValue<StringFieldValue, Struct> structmap1 = new MapFieldValue<>((MapDataType)l1s1.getField("structmap").getDataType());
        structmap1.put(new StringFieldValue("test"), l2s1.clone());
        l1s1.setFieldValue("structmap", structmap1);

        WeightedSet<StringFieldValue> wset1 = new WeightedSet<>(l1s1.getField("wset").getDataType());
        wset1.add(new StringFieldValue("foo"));
        wset1.add(new StringFieldValue("bar"));
        wset1.add(new StringFieldValue("zoo"));
        l1s1.setFieldValue("wset", wset1);

        Struct l2s2 = new Struct(doc.getField("struct2").getDataType());
        l2s2.setFieldValue("primitive1", 5);
        l2s2.setFieldValue("primitive2", 6);

        WeightedSet<Struct> wset2 = new WeightedSet<>(l1s1.getField("structwset").getDataType());
        wset2.add(l2s1.clone());
        wset2.add(l2s2.clone());
        l1s1.setFieldValue("structwset", wset2);

        doc.setFieldValue("l1s1", l1s1.clone());

        {
            ModifyIteratorHandler handler = new ModifyIteratorHandler();

            FieldPath path = doc.getDataType().buildFieldPath("l1s1.structmap.value.smap{leonardo}");
            doc.iterateNested(path, 0, handler);

            FieldValue fv = doc.getRecursiveValue("l1s1.structmap.value.smap{leonardo}");
            assertEquals(new StringFieldValue("newvalue"), fv);
        }

        {
            AddIteratorHandler handler = new AddIteratorHandler();
            FieldPath path = doc.getDataType().buildFieldPath("l1s1.ss.iarray");
            doc.iterateNested(path, 0, handler);

            FieldValue fv = doc.getRecursiveValue("l1s1.ss.iarray");
            assertTrue(((Array)fv).contains(new IntegerFieldValue(32)));
            assertEquals(4, ((Array)fv).size());
        }

        {
            RemoveIteratorHandler handler = new RemoveIteratorHandler();
            FieldPath path = doc.getDataType().buildFieldPath("l1s1.ss.iarray[1]");
            doc.iterateNested(path, 0, handler);

            FieldValue fv = doc.getRecursiveValue("l1s1.ss.iarray");

            assertFalse(((Array)fv).contains(new Integer(12)));
            assertEquals(3, ((Array)fv).size());
        }

        {
            RemoveIteratorHandler handler = new RemoveIteratorHandler();
            FieldPath path = doc.getDataType().buildFieldPath("l1s1.ss.iarray[$x]");
            doc.iterateNested(path, 0, handler);

            FieldValue fv = doc.getRecursiveValue("l1s1.ss.iarray");
            assertEquals(0, ((Array)fv).size());
        }

        {
            RemoveIteratorHandler handler = new RemoveIteratorHandler();
            FieldPath path = doc.getDataType().buildFieldPath("l1s1.structmap.value.smap{leonardo}");
            doc.iterateNested(path, 0, handler);

            FieldValue fv = doc.getRecursiveValue("l1s1.structmap.value.smap");
            assertFalse(((MapFieldValue)fv).contains(new StringFieldValue("leonardo")));
        }

        {
            RemoveIteratorHandler handler = new RemoveIteratorHandler();
            FieldPath path = doc.getDataType().buildFieldPath("l1s1.wset{foo}");
            doc.iterateNested(path, 0, handler);

            FieldValue fv = doc.getRecursiveValue("l1s1.wset");
            assertFalse(((WeightedSet)fv).contains(new StringFieldValue("foo")));
        }
    }

    @Test
    public void testNoType() {
        try {
            new Document(null, new DocumentId("doc:null:URI"));
            fail("Should have gotten an Exception");
        } catch (NullPointerException | IllegalArgumentException e) {
            // Success
        }
    }

    @Test
    public void testURI() {
        String uri = "doc:testdoc:http://www.ntnu.no/";

        DocumentType documentType = docMan.getDocumentType("testdoc");
        assertNotNull(documentType);
        Document doc = new Document(docMan.getDocumentType("testdoc"), new DocumentId(uri));
        assertEquals(doc.getId().toString(), uri);
    }

    @Test
    public void testSetGet() {
        Document doc = new Document(docMan.getDocumentType("testdoc"), new DocumentId("doc:testdoc:test"));
        Object val = doc.getFieldValue(minField.getName());
        assertNull(val);
        doc.setFieldValue(minField.getName(), 500);
        val = doc.getFieldValue(minField.getName());
        assertEquals(new IntegerFieldValue(500), val);
        val = doc.getFieldValue(minField.getName());
        assertEquals(new IntegerFieldValue(500), val);
        doc.removeFieldValue(minField);
        assertNull(doc.getFieldValue(minField.getName()));
        assertNull(doc.getFieldValue("doesntexist"));
    }

    @Test
    public void testGetField() {
        Document doc = getTestDocument();

        assertNull(doc.getFieldValue("doesntexist"));
        assertNull(doc.getFieldValue("notintype"));
    }

    @Test
    public void testCppDocCompressed() throws IOException {
        docMan = setUpCppDocType();
        byte[] data = readFile("src/test/document/serializecpp-lz4-level9.dat");
        ByteBuffer buf = ByteBuffer.wrap(data);

        Document doc = docMan.createDocument(new GrowableByteBuffer(buf));

        validateCppDoc(doc);
    }

    @Test
    public void testCppDoc() throws IOException {
        docMan = setUpCppDocType();
        byte[] data = readFile("src/test/document/serializecpp.dat");
        ByteBuffer buf = ByteBuffer.wrap(data);

        Document doc = docMan.createDocument(new GrowableByteBuffer(buf));
        validateCppDoc(doc);
    }

    @Test
    public void testV6Doc() throws IOException {
        docMan = setUpCppDocType();
        byte[] data = readFile("src/tests/data/serializev6.dat");
        ByteBuffer buf = ByteBuffer.wrap(data);

        Document doc = docMan.createDocument(new GrowableByteBuffer(buf));
        validateCppDocNotMap(doc);
    }

    public void validateCppDoc(Document doc) throws IOException {
        validateCppDocNotMap(doc);
        MapFieldValue map = (MapFieldValue)doc.getFieldValue("mapfield");
        assertEquals(map.get(new StringFieldValue("foo1")), new StringFieldValue("bar1"));
        assertEquals(map.get(new StringFieldValue("foo2")), new StringFieldValue("bar2"));
    }

    @SuppressWarnings("unchecked")
    public void validateCppDocNotMap(Document doc) throws IOException {
        // in practice to validate v6 serialization
        assertEquals("doc:serializetest:http://test.doc.id/", doc.getId().toString());
        assertEquals(new IntegerFieldValue(5), doc.getFieldValue("intfield"));
        assertEquals(new FloatFieldValue((float)-9.23), doc.getFieldValue("floatfield"));
        assertEquals(new StringFieldValue("This is a string."), doc.getFieldValue("stringfield"));
        assertEquals(new LongFieldValue(398420092938472983L), doc.getFieldValue("longfield"));
        assertEquals(new DoubleFieldValue(98374532.398820d), doc.getFieldValue("doublefield"));
        assertEquals(new StringFieldValue("http://this.is.a.test/"), doc.getFieldValue("urifield"));
        //NOTE: The value really is unsigned 254, which becomes signed -2:
        assertEquals(new ByteFieldValue(-2), doc.getFieldValue("bytefield"));
        ByteBuffer raw = ByteBuffer.wrap("RAW DATA".getBytes());
        assertEquals(new Raw(raw), doc.getFieldValue("rawfield"));

        Document docindoc = (Document)doc.getFieldValue("docfield");
        assertEquals(docMan.getDocumentType("docindoc"), docindoc.getDataType());
        assertEquals(new DocumentId("doc:docindoc:http://embedded"), docindoc.getId());

        Array<FloatFieldValue> array = (Array<FloatFieldValue>)doc.getFieldValue("arrayoffloatfield");
        assertEquals(new FloatFieldValue(1.0f), array.get(0));
        assertEquals(new FloatFieldValue(2.0f), array.get(1));

        WeightedSet<StringFieldValue> wset = (WeightedSet<StringFieldValue>)doc.getFieldValue("wsfield");
        assertEquals(new Integer(50), wset.get(new StringFieldValue("Weighted 0")));
        assertEquals(new Integer(199), wset.get(new StringFieldValue("Weighted 1")));
    }

    @Test
    public void testCppDocSplit() throws IOException {
        docMan = setUpCppDocType();
        byte[] headerData = readFile("src/test/document/serializecppsplit_header.dat");
        byte[] bodyData = readFile("src/test/document/serializecppsplit_body.dat");

        DocumentDeserializer header = DocumentDeserializerFactory.create42(docMan, GrowableByteBuffer.wrap(headerData),
                GrowableByteBuffer.wrap(bodyData));

        Document doc = new Document(header);

        assertEquals("doc:serializetest:http://test.doc.id/", doc.getId().toString());
        assertEquals(new IntegerFieldValue(5), doc.getFieldValue("intfield"));
        assertEquals(new FloatFieldValue((float)-9.23), doc.getFieldValue("floatfield"));
        assertEquals(new StringFieldValue("This is a string."), doc.getFieldValue("stringfield"));
        assertEquals(new LongFieldValue(398420092938472983L), doc.getFieldValue("longfield"));
        assertEquals(new DoubleFieldValue(98374532.398820d), doc.getFieldValue("doublefield"));
        assertEquals(new StringFieldValue("http://this.is.a.test/"), doc.getFieldValue("urifield"));
        //NOTE: The value really is unsigned 254, which becomes signed -2:
        assertEquals(new ByteFieldValue((byte)-2), doc.getFieldValue("bytefield"));
        ByteBuffer raw = ByteBuffer.wrap("RAW DATA".getBytes());
        assertEquals(new Raw(raw), doc.getFieldValue("rawfield"));

        Document docindoc = (Document)doc.getFieldValue("docfield");
        assertEquals(docMan.getDocumentType("docindoc"), docindoc.getDataType());
        assertEquals(new DocumentId("doc:docindoc:http://embedded"), docindoc.getId());

        WeightedSet wset = (WeightedSet)doc.getFieldValue("wsfield");
        assertEquals(new Integer(50), wset.get(new StringFieldValue("Weighted 0")));
        assertEquals(new Integer(199), wset.get(new StringFieldValue("Weighted 1")));
    }

    @Test
    public void testCppDocSplitNoBody() throws IOException {
        docMan = setUpCppDocType();
        byte[] headerData = readFile("src/test/document/serializecppsplit_header.dat");

        DocumentDeserializer header = DocumentDeserializerFactory.create42(docMan, GrowableByteBuffer.wrap(headerData));

        Document doc = new Document(header);

        assertEquals("doc:serializetest:http://test.doc.id/", doc.getId().toString());
        assertEquals(new FloatFieldValue((float)-9.23), doc.getFieldValue("floatfield"));
        assertEquals(new StringFieldValue("This is a string."), doc.getFieldValue("stringfield"));
        assertEquals(new LongFieldValue(398420092938472983L), doc.getFieldValue("longfield"));
        assertEquals(new StringFieldValue("http://this.is.a.test/"), doc.getFieldValue("urifield"));
    }

    @Test
    public void testGenerateSerializedFile() throws IOException {

        docMan = setUpCppDocType();
        Document doc = new Document(docMan.getDocumentType("serializetest"),
                                    new DocumentId("doc:serializetest:http://test.doc.id/"));

        Document docindoc = new Document(docMan.getDocumentType("docindoc"),
                                         new DocumentId("doc:serializetest:http://doc.in.doc/"));
        docindoc.setFieldValue("stringindocfield", "Elvis is dead");
        doc.setFieldValue("docfield", docindoc);

        Array<FloatFieldValue> l = new Array<>(doc.getField("arrayoffloatfield").getDataType());
        l.add(new FloatFieldValue((float)1.0));
        l.add(new FloatFieldValue((float)2.0));
        doc.setFieldValue("arrayoffloatfield", l);

        WeightedSet<StringFieldValue>
                wset = new WeightedSet<>(doc.getDataType().getField("wsfield").getDataType());
        wset.put(new StringFieldValue("Weighted 0"), 50);
        wset.put(new StringFieldValue("Weighted 1"), 199);
        doc.setFieldValue("wsfield", wset);

        MapFieldValue<StringFieldValue, StringFieldValue> map =
                new MapFieldValue<>(
                        (MapDataType)doc.getDataType().getField("mapfield").getDataType());
        map.put(new StringFieldValue("foo1"), new StringFieldValue("bar1"));
        map.put(new StringFieldValue("foo2"), new StringFieldValue("bar2"));
        doc.setFieldValue("mapfield", map);

        doc.setFieldValue("bytefield", new ByteFieldValue((byte)254));
        doc.setFieldValue("rawfield", new Raw(ByteBuffer.wrap("RAW DATA".getBytes())));
        doc.setFieldValue("intfield", new IntegerFieldValue(5));
        doc.setFieldValue("floatfield", new FloatFieldValue(-9.23f));
        doc.setFieldValue("stringfield", new StringFieldValue("This is a string."));
        doc.setFieldValue("longfield", new LongFieldValue(398420092938472983L));
        doc.setFieldValue("doublefield", new DoubleFieldValue(98374532.398820d));
        doc.setFieldValue("urifield", new StringFieldValue("http://this.is.a.test/"));

        int size = doc.getSerializedSize();
        GrowableByteBuffer buf = new GrowableByteBuffer(size, 2.0f);

        doc.serialize(buf);
        assertEquals(size, buf.position());

        buf.position(0);

        FileOutputStream fos = new FileOutputStream("src/tests/data/serializejava.dat");
        fos.write(buf.array(), 0, size);
        fos.close();

        CompressionConfig noncomp = new CompressionConfig();
        CompressionConfig lz4comp = new CompressionConfig(CompressionType.LZ4);

        doc.getDataType().getHeaderType().setCompressionConfig(lz4comp);
        doc.getDataType().getBodyType().setCompressionConfig(lz4comp);
        buf = new GrowableByteBuffer(size, 2.0f);

        doc.serialize(buf);
        doc.getDataType().getHeaderType().setCompressionConfig(noncomp);
        doc.getDataType().getBodyType().setCompressionConfig(noncomp);
        fos = new FileOutputStream("src/tests/data/serializejava-compressed.dat");
        fos.write(buf.array(), 0, buf.position());
        fos.close();
    }

    @Test
    public void testSerializeDeserialize() {
        setUpSertestDocType();
        Document doc = getSertestDocument();

        GrowableByteBuffer data = new GrowableByteBuffer();
        doc.serialize(data);
        int size = doc.getSerializedSize();

        assertEquals(size, data.position());

        data.flip();

        try {
            FileOutputStream fos = new FileOutputStream("src/test/files/testser.dat");
            fos.write(data.array(), 0, data.remaining());
            fos.close();
        } catch (Exception e) {
        }

        Document doc2 = docMan.createDocument(data);

        assertEquals(doc.getFieldValue("mailid"), doc2.getFieldValue("mailid"));
        assertEquals(doc.getFieldValue("date"), doc2.getFieldValue("date"));
        assertEquals(doc.getFieldValue("from"), doc2.getFieldValue("from"));
        assertEquals(doc.getFieldValue("to"), doc2.getFieldValue("to"));
        assertEquals(doc.getFieldValue("subject"), doc2.getFieldValue("subject"));
        assertEquals(doc.getFieldValue("body"), doc2.getFieldValue("body"));
        assertEquals(doc.getFieldValue("attachmentcount"), doc2.getFieldValue("attachmentcount"));
        assertEquals(doc.getFieldValue("attachments"), doc2.getFieldValue("attachments"));
        byte[] docRawBytes = ((Raw)doc.getFieldValue("rawfield")).getByteBuffer().array();
        byte[] doc2RawBytes = ((Raw)doc2.getFieldValue("rawfield")).getByteBuffer().array();
        assertEquals(docRawBytes.length, doc2RawBytes.length);
        for (int i = 0; i < docRawBytes.length; i++) {
            assertEquals(docRawBytes[i], doc2RawBytes[i]);
        }
        assertEquals(doc.getFieldValue("weightedfield"), doc2.getFieldValue("weightedfield"));
        assertEquals(doc.getFieldValue("mapfield"), doc2.getFieldValue("mapfield"));
        // Do the same thing, splitting document in two
        DocumentSerializer header = DocumentSerializerFactory.create42(new GrowableByteBuffer(), true);
        DocumentSerializer body = DocumentSerializerFactory.create42(new GrowableByteBuffer());
        doc.serializeHeader(header);
        doc.serializeBody(body);
        header.getBuf().flip();
        body.getBuf().flip();

        try {
            FileOutputStream fos = new FileOutputStream("src/test/files/testser-split.header.dat");
            fos.write(header.getBuf().array(), 0, header.getBuf().remaining());
            fos.close();
            fos = new FileOutputStream("src/test/files/testser-split.body.dat");
            fos.write(body.getBuf().array(), 0, body.getBuf().remaining());
            fos.close();
        } catch (Exception e) {
        }

        DocumentDeserializer deser = DocumentDeserializerFactory.create42(docMan, header.getBuf(), body.getBuf());

        doc2 = new Document(deser);

        assertEquals(doc.getFieldValue("mailid"), doc2.getFieldValue("mailid"));
        assertEquals(doc.getFieldValue("date"), doc2.getFieldValue("date"));
        assertEquals(doc.getFieldValue("from"), doc2.getFieldValue("from"));
        assertEquals(doc.getFieldValue("to"), doc2.getFieldValue("to"));
        assertEquals(doc.getFieldValue("subject"), doc2.getFieldValue("subject"));
        assertEquals(doc.getFieldValue("body"), doc2.getFieldValue("body"));
        assertEquals(doc.getFieldValue("attachmentcount"), doc2.getFieldValue("attachmentcount"));
        assertEquals(doc.getFieldValue("attachments"), doc2.getFieldValue("attachments"));
        docRawBytes = ((Raw)doc.getFieldValue("rawfield")).getByteBuffer().array();
        doc2RawBytes = ((Raw)doc2.getFieldValue("rawfield")).getByteBuffer().array();
        assertEquals(docRawBytes.length, doc2RawBytes.length);
        for (int i = 0; i < docRawBytes.length; i++) {
            assertEquals(docRawBytes[i], doc2RawBytes[i]);
        }
        assertEquals(doc.getFieldValue("weightedfield"), doc2.getFieldValue("weightedfield"));
        assertEquals(doc.getFieldValue("mapfield"), doc2.getFieldValue("mapfield"));

        Document docInDoc = (Document)doc.getFieldValue("docindoc");
        assert (docInDoc != null);
        assertEquals(new StringFieldValue("ball"), docInDoc.getFieldValue("tull"));
    }

    @Test
    public void testSerializeDeserializeCompressed() {
        setUpSertestDocType();
        Document doc = getSertestDocument();

        CompressionConfig noncomp = new CompressionConfig();
        CompressionConfig lz4comp = new CompressionConfig(CompressionType.LZ4);

        doc.getDataType().getHeaderType().setCompressionConfig(lz4comp);
        doc.getDataType().getBodyType().setCompressionConfig(lz4comp);

        GrowableByteBuffer data = new GrowableByteBuffer();
        doc.serialize(data);
        int size = doc.getSerializedSize();
        doc.getDataType().getHeaderType().setCompressionConfig(noncomp);
        doc.getDataType().getBodyType().setCompressionConfig(noncomp);

        assertEquals(size, data.position());

        data.flip();

        try {
            FileOutputStream fos = new FileOutputStream("src/test/files/testser.dat");
            fos.write(data.array(), 0, data.remaining());
            fos.close();
        } catch (Exception e) {
        }

        Document doc2 = docMan.createDocument(data);

        assertEquals(doc.getFieldValue("mailid"), doc2.getFieldValue("mailid"));
        assertEquals(doc.getFieldValue("date"), doc2.getFieldValue("date"));
        assertEquals(doc.getFieldValue("from"), doc2.getFieldValue("from"));
        assertEquals(doc.getFieldValue("to"), doc2.getFieldValue("to"));
        assertEquals(doc.getFieldValue("subject"), doc2.getFieldValue("subject"));
        assertEquals(doc.getFieldValue("body"), doc2.getFieldValue("body"));
        assertEquals(doc.getFieldValue("attachmentcount"), doc2.getFieldValue("attachmentcount"));
        assertEquals(doc.getFieldValue("attachments"), doc2.getFieldValue("attachments"));
        byte[] docRawBytes = ((Raw)doc.getFieldValue("rawfield")).getByteBuffer().array();
        byte[] doc2RawBytes = ((Raw)doc2.getFieldValue("rawfield")).getByteBuffer().array();
        assertEquals(docRawBytes.length, doc2RawBytes.length);
        for (int i = 0; i < docRawBytes.length; i++) {
            assertEquals(docRawBytes[i], doc2RawBytes[i]);
        }
        assertEquals(doc.getFieldValue("weightedfield"), doc2.getFieldValue("weightedfield"));
        assertEquals(doc.getFieldValue("mapfield"), doc2.getFieldValue("mapfield"));

        // Do the same thing, splitting document in two
        BufferSerializer header = new BufferSerializer(new GrowableByteBuffer());
        BufferSerializer body = new BufferSerializer(new GrowableByteBuffer());
        doc.serializeHeader(header);
        doc.serializeBody(body);
        header.getBuf().flip();
        body.getBuf().flip();

        try {
            FileOutputStream fos = new FileOutputStream("src/test/files/testser-split.header.dat");
            fos.write(header.getBuf().array(), 0, header.getBuf().remaining());
            fos.close();
            fos = new FileOutputStream("src/test/files/testser-split.body.dat");
            fos.write(body.getBuf().array(), 0, body.getBuf().remaining());
            fos.close();
        } catch (Exception e) {
        }

        DocumentDeserializer deser = DocumentDeserializerFactory.create42(docMan, header.getBuf(), body.getBuf());

        doc2 = new Document(deser);

        assertEquals(doc.getFieldValue("mailid"), doc2.getFieldValue("mailid"));
        assertEquals(doc.getFieldValue("date"), doc2.getFieldValue("date"));
        assertEquals(doc.getFieldValue("from"), doc2.getFieldValue("from"));
        assertEquals(doc.getFieldValue("to"), doc2.getFieldValue("to"));
        assertEquals(doc.getFieldValue("subject"), doc2.getFieldValue("subject"));
        assertEquals(doc.getFieldValue("body"), doc2.getFieldValue("body"));
        assertEquals(doc.getFieldValue("attachmentcount"), doc2.getFieldValue("attachmentcount"));
        assertEquals(doc.getFieldValue("attachments"), doc2.getFieldValue("attachments"));
        docRawBytes = ((Raw)doc.getFieldValue("rawfield")).getByteBuffer().array();
        doc2RawBytes = ((Raw)doc2.getFieldValue("rawfield")).getByteBuffer().array();
        assertEquals(docRawBytes.length, doc2RawBytes.length);
        for (int i = 0; i < docRawBytes.length; i++) {
            assertEquals(docRawBytes[i], doc2RawBytes[i]);
        }
        assertEquals(doc.getFieldValue("weightedfield"), doc2.getFieldValue("weightedfield"));
        assertEquals(doc.getFieldValue("mapfield"), doc2.getFieldValue("mapfield"));
    }

    @Test
    public void testDeserialize() {
        setUpSertestDocType();

        BufferSerializer buf = new BufferSerializer();
        try {
            new Document(DocumentDeserializerFactory.create42(docMan, buf.getBuf()));
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }

        buf = BufferSerializer.wrap("Hello world".getBytes());
        try {
            new Document(DocumentDeserializerFactory.create42(docMan, buf.getBuf()));
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void testInheritance() {
        // Create types that inherit each other.. And test that it works..
        DocumentType parentType = new DocumentType("parent");
        parentType.addField(new Field("parentbodyint", DataType.INT, false));
        parentType.addField(new Field("parentheaderint", DataType.INT, true));
        parentType.addField(new Field("overwritten", DataType.INT, true));
        DocumentType childType = new DocumentType("child");
        childType.addField(new Field("childbodyint", DataType.INT, false));
        childType.addField(new Field("childheaderint", DataType.INT, true));
        childType.addField(new Field("overwritten", DataType.INT, true));
        childType.inherit(parentType);

        DocumentTypeManager manager = new DocumentTypeManager();
        manager.register(childType);

        Document child = new Document(childType, new DocumentId("doc:what:test"));
        child.setFieldValue(childType.getField("parentbodyint"), new IntegerFieldValue(4));
        child.setFieldValue("parentheaderint", 6);
        child.setFieldValue("overwritten", 7);
        child.setFieldValue("childbodyint", 14);

        GrowableByteBuffer buffer = new GrowableByteBuffer(1024, 2f);
        child.serialize(buffer);
        buffer.flip();
        Document childCopy = manager.createDocument(buffer);

        // Test various ways of retrieving values
        assertEquals(new IntegerFieldValue(4), childCopy.getFieldValue(childType.getField("parentbodyint")));
        assertEquals(new IntegerFieldValue(6), childCopy.getFieldValue("parentheaderint"));
        assertEquals(new IntegerFieldValue(7), childCopy.getFieldValue("overwritten"));

        assertEquals(child, childCopy);
    }

    @Test
    public void testInheritanceTypeMismatch() {
        DocumentType parentType = new DocumentType("parent");
        parentType.addField(new Field("parentbodyint", DataType.INT, false));
        parentType.addField(new Field("parentheaderint", DataType.INT, true));
        parentType.addField(new Field("overwritten", DataType.STRING, true));
        DocumentType childType = new DocumentType("child");
        childType.addField(new Field("childbodyint", DataType.INT, false));
        childType.addField(new Field("childheaderint", DataType.INT, true));
        childType.addField(new Field("overwritten", DataType.INT, true));
        try {
            childType.inherit(parentType);
            fail("Inheritance with conflicting types worked.");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(),
                         "Inheritance type mismatch: field \"overwritten\" in datatype \"child\" must have same datatype as in parent document type \"parent\"");
        }
    }

    @Test
    public void testFieldValueImplementations() {
        docMan = new DocumentTypeManager();
        DocumentType docType = new DocumentType("impl");
        docType.addField(new Field("something", DataType.getArray(DataType.STRING), false));
        docMan.register(docType);

        //just checks that isAssignableFrom() in Document.setFieldValue() goes the right way

        Document doc = new Document(docMan.getDocumentType("impl"), new DocumentId("doc:doctest:fooooobardoc"));
        Array<StringFieldValue> testlist = new Array<>(doc.getField("something").getDataType());
        doc.setFieldValue("something", testlist);
    }

    @Test
    public void testCompressionConfigured() {

        int size_uncompressed;
        {
            DocumentTypeManager docMan = new DocumentTypeManager();
            docMan.configure("file:src/tests/data/cppdocument.cfg");

            Document doc = new Document(docMan.getDocumentType("serializetest"), new DocumentId("doc:test:test"));

            doc.setFieldValue("stringfield",
                              "compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me ");

            GrowableByteBuffer data = new GrowableByteBuffer();
            doc.serialize(data);
            size_uncompressed = data.position();
        }

        DocumentTypeManager docMan = new DocumentTypeManager();
        docMan.configure("file:src/tests/data/compressed.cfg");

        Document doc = new Document(docMan.getDocumentType("serializetest"), new DocumentId("doc:test:test"));

        doc.setFieldValue("stringfield",
                          "compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me compress me ");

        GrowableByteBuffer data = new GrowableByteBuffer();
        doc.serialize(data);
        int size_compressed = data.position();

        assertTrue(size_compressed + " < " + size_uncompressed, size_compressed < size_uncompressed);
    }

    @Test
    public void testDocumentDataType() {
        //use documenttypemanagerconfigurer to read config
        docMan = new DocumentTypeManager();
        DocumentTypeManagerConfigurer.configure(docMan, "file:src/test/document/docindoc.cfg");

        //get a document type
        docMan.getDocumentType("outerdoc");

        //create document and necessary structures
        Document outerdoc = new Document(docMan.getDocumentType("outerdoc"), new DocumentId("doc:recursion:outerdoc"));
        Document innerdoc = new Document(docMan.getDocumentType("innerdoc"), new DocumentId("doc:recursion:innerdoc"));

        innerdoc.setFieldValue("intfield", 55);

        outerdoc.setFieldValue("stringfield", "boooo");
        outerdoc.setFieldValue("docfield", innerdoc);

        //serialize document
        int size = outerdoc.getSerializedSize();
        GrowableByteBuffer buf = new GrowableByteBuffer(size, 2.0f);
        outerdoc.serialize(buf);

        assertEquals(size, buf.position());

        //deserialize document
        buf.position(0);
        Document outerdoc2 = docMan.createDocument(buf);

        //compare values
        assertEquals(outerdoc, outerdoc2);
    }

    @Test
    public void testTimestamp() {
        Document doc = new Document(docMan.getDocumentType("testdoc"), new DocumentId("doc:testdoc:timetest"));
        assertNull(doc.getLastModified());
        doc.setLastModified(4350129845023985L);
        assertEquals(new Long(4350129845023985L), doc.getLastModified());
        doc.setLastModified(null);
        assertNull(doc.getLastModified());

        Long timestamp = System.currentTimeMillis();
        doc.setLastModified(timestamp);
        assertEquals(timestamp, doc.getLastModified());

        GrowableByteBuffer buf = new GrowableByteBuffer();
        doc.getSerializedSize();
        doc.serialize(buf);
        buf.position(0);

        Document doc2 = docMan.createDocument(buf);
        assertNull(doc2.getLastModified());
    }

    @Test
    public void testToXml() {
        setUpSertestDocType();
        Document doc = getSertestDocument();
        doc.setFieldValue("mailid", "emailfromalicetobob&someone");
        // Pizza Hut Sunnyvale: x="-122057174" y="37374821" latlong="N37.374821;W122.057174"
        doc.setFieldValue("myposfield", PositionDataType.valueOf(-122057174, 37374821));
        String xml = doc.toXML(" ");
        System.out.println(xml);
        assertTrue(xml.contains(SERTEST_DOC_AS_XML_HEAD));
        assertTrue(xml.contains(SERTEST_DOC_AS_XML_FOOT));
        assertTrue(xml.contains(SERTEST_DOC_AS_XML_WEIGHT1) || xml.contains(SERTEST_DOC_AS_XML_WEIGHT2));
        assertTrue(xml.contains(SERTEST_DOC_AS_XML_SUNNYVALE));
    }

    @Test
    public void testXmlSerializer() {
        setUpSertestDocType();
        Document doc = getSertestDocument();
        doc.setFieldValue("mailid", "emailfromalicetobob&someone");
        doc.setFieldValue("myposfield", PositionDataType.valueOf(-122057174, 37374821));
        String xml = doc.toXML(" ");
        XmlDocumentWriter w = XmlDocumentWriter.createWriter(" ");
        w.write(doc);
        String otherXml = doc.toXML(" ");
        assertEquals(xml, otherXml);
    }

    @Test
    public void testSingleFieldToXml() {
        Document doc = new Document(docMan.getDocumentType("testdoc"), new DocumentId("doc:testdoc:xmltest"));
        doc.setFieldValue("stringattr", new StringFieldValue("hello world"));
        assertEquals("<value>hello world</value>\n", doc.getFieldValue("stringattr").toXml());
    }

    @Test
    public void testDelegatedDocumentToXml() {
        Document doc = new Document(docMan.getDocumentType("testdoc"), new DocumentId("doc:testdoc:xmltest"));
        doc.setFieldValue("stringattr", new StringFieldValue("hello universe"));
        // Should just delegate to toXML
        assertEquals(
                "<document documenttype=\"testdoc\" documentid=\"doc:testdoc:xmltest\">\n" +
                "  <stringattr>hello universe</stringattr>\n" +
                "</document>\n",
                doc.toXml());
    }

    @Test
    public void testEmptyStringsSerialization() {
        docMan = new DocumentTypeManager();
        DocumentType docType = new DocumentType("emptystrings");
        docType.addField(new Field("emptystring", DataType.STRING));
        docType.addField(new Field("nullstring", DataType.STRING));
        docType.addField(new Field("spacestring", DataType.STRING));
        docType.addField(new Field("astring", DataType.STRING));
        docMan.registerDocumentType(docType);

        GrowableByteBuffer grbuf = new GrowableByteBuffer();
        {
            Document doc = new Document(docType, new DocumentId("doc:a:b:emptystrings"));

            doc.setFieldValue("emptystring", "");
            doc.removeFieldValue("nullstring");
            doc.setFieldValue("spacestring", " ");
            doc.setFieldValue("astring", "a");
            assertEquals(new StringFieldValue(""), doc.getFieldValue("emptystring"));
            assertNull(doc.getFieldValue("nullstring"));
            assertEquals(new StringFieldValue(" "), doc.getFieldValue("spacestring"));
            assertEquals(new StringFieldValue("a"), doc.getFieldValue("astring"));

            doc.getSerializedSize();
            doc.serialize(grbuf);

            grbuf.flip();
        }
        {
            Document doc2 = docMan.createDocument(grbuf);

            assertEquals(new StringFieldValue(""), doc2.getFieldValue("emptystring"));
            assertNull(doc2.getFieldValue("nullstring"));
            assertEquals(new StringFieldValue(" "), doc2.getFieldValue("spacestring"));
            assertEquals(new StringFieldValue("a"), doc2.getFieldValue("astring"));

        }
    }

    @Test
    public void testBug2354045() {
        DocumentTypeManager docMan = new DocumentTypeManager();
        DocumentType docType = new DocumentType("bug2354045");
        docType.addField(new Field("string", DataType.STRING));
        docMan.register(docType);

        GrowableByteBuffer grbuf = new GrowableByteBuffer();

        Document doc = new Document(docType, new DocumentId("doc:a:b:strings"));

        doc.removeFieldValue("string");
        assertNull(doc.getFieldValue("string"));

        doc.getSerializedSize();
        doc.serialize(grbuf);

        grbuf.flip();

        Document doc2 = docMan.createDocument(grbuf);
        assertNull(doc2.getFieldValue("string"));
        assertEquals(doc, doc2);
    }

    @Test
    public void testUnknownFieldsDeserialization() {
        DocumentTypeManager docTypeManasjer = new DocumentTypeManager();

        GrowableByteBuffer buf = new GrowableByteBuffer();

        {
            DocumentType typeWithDinner = new DocumentType("elvis");
            typeWithDinner.addField("breakfast", DataType.STRING);
            typeWithDinner.addField("lunch", DataType.INT);
            typeWithDinner.addField("dinner", DataType.DOUBLE);
            docTypeManasjer.registerDocumentType(typeWithDinner);

            Document docWithDinner = new Document(typeWithDinner, "doc:elvis:has:left:the:building");
            docWithDinner.setFieldValue("breakfast", "peanut butter");
            docWithDinner.setFieldValue("lunch", 14);
            docWithDinner.setFieldValue("dinner", 5.43d);

            docWithDinner.serialize(buf);
            buf.flip();

            docTypeManasjer.clear();
        }

        {
            DocumentType typeWithoutDinner = new DocumentType("elvis");
            typeWithoutDinner.addField("breakfast", DataType.STRING);
            typeWithoutDinner.addField("lunch", DataType.INT);
            //no dinner
            docTypeManasjer.registerDocumentType(typeWithoutDinner);

            Document docWithoutDinner = docTypeManasjer.createDocument(buf);

            assertEquals(new StringFieldValue("peanut butter"), docWithoutDinner.getFieldValue("breakfast"));
            assertEquals(new IntegerFieldValue(14), docWithoutDinner.getFieldValue("lunch"));
            assertNull(docWithoutDinner.getFieldValue("dinner"));
        }
    }

    @Test
    public void testBug3233988() {
        DocumentType type = new DocumentType("foo");
        Field field = new Field("productdesc", DataType.STRING);
        type.addField(field);

        Document doc;

        doc = new Document(type, "doc:foo:bar:bar");
        doc.removeFieldValue("productdesc");
        assertNull(doc.getFieldValue("productdesc"));

        doc = new Document(type, "doc:foo:bar:bar");
        assertNull(doc.getFieldValue("productdesc"));
    }

    @Test
    public void testRequireThatDocumentWithIdSchemaIdChecksType() {
        DocumentType docType = new DocumentType("mytype");
        try {
            new Document(docType, "id:namespace:mytype::foo");
        } catch (Exception e) {
            fail();
        }

        try {
            new Document(docType, "id:namespace:wrong-type::foo");
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    private class MyDocumentReader implements DocumentReader {

        @Override
        public void read(Document document) {
        }

        @Override
        public DocumentId readDocumentId() {
            return null;
        }

        @Override
        public DocumentType readDocumentType() {
            return null;
        }

    }

    @Test
    public void testRequireThatChangingDocumentTypeChecksId() {
        MyDocumentReader reader = new MyDocumentReader();
        Document doc = new Document(reader);
        doc.setId(new DocumentId("id:namespace:mytype::foo"));
        DocumentType docType = new DocumentType("mytype");
        try {
            doc.setDataType(docType);
        } catch (Exception e) {
            fail();
        }
        doc = new Document(reader);
        doc.setId(new DocumentId("id:namespace:mytype::foo"));
        DocumentType wrongType = new DocumentType("wrongtype");
        try {
            doc.setDataType(wrongType);
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testDocumentComparisonDoesNotCorruptStateBug6394548() {
        DocumentTypeManager docMan = new DocumentTypeManager();
        DocumentType docType = new DocumentType("bug2354045");
        docType.addField(new Field("string", 2, DataType.STRING, true));
        docType.addField(new Field("int", 1, DataType.INT, true));
        docType.addField(new Field("float", 0, DataType.FLOAT, true));
        docMan.register(docType);

        Document doc1 = new Document(docType, new DocumentId("doc:a:b:bug6394548"));
        doc1.setFieldValue("string", new StringFieldValue("hello world"));
        doc1.setFieldValue("int", new IntegerFieldValue(1234));
        doc1.setFieldValue("float", new FloatFieldValue(5.5f));
        String doc1Before = doc1.toXml();

        Document doc2 = new Document(docType, new DocumentId("doc:a:b:bug6394548"));
        doc2.setFieldValue("string", new StringFieldValue("aardvark"));
        doc2.setFieldValue("int", new IntegerFieldValue(90909));
        doc2.setFieldValue("float", new FloatFieldValue(777.15f));
        String doc2Before = doc2.toXml();

        doc1.compareTo(doc2);

        String doc1After = doc1.toXml();
        String doc2After = doc2.toXml();

        assertEquals(doc1Before, doc1After);
        assertEquals(doc2Before, doc2After);
    }

    private static class DocumentIdFixture {
        private final DocumentTypeManager docMan = new DocumentTypeManager();
        private final DocumentType docType = new DocumentType("b");
        private final GrowableByteBuffer buffer = new GrowableByteBuffer();
        public DocumentIdFixture() {
            docMan.register(docType);
        }
        public void serialize(String docId) {
            new Document(docType, DocumentId.createFromSerialized(docId))
                    .serialize(DocumentSerializerFactory.createHead(buffer));
            buffer.flip();
        }
        public Document deserialize() {
            return new Document(DocumentDeserializerFactory.createHead(docMan, buffer));
        }
    }

    @Test
    public void testDocumentIdWithNonTextCharacterCanBeDeserialized() throws UnsupportedEncodingException {
        DocumentIdFixture f = new DocumentIdFixture();

        // Document id = "id:a:b::0x7c"
        String docId = new String(new byte[]{105, 100, 58, 97, 58, 98, 58, 58, 7, 99}, "UTF-8");
        f.serialize(docId);

        Document result = f.deserialize();
        assertEquals(docId, result.getId().toString());
    }

}
