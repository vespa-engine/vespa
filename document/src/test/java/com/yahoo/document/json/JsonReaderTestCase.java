// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.google.common.base.Joiner;
import com.yahoo.collections.Tuple2;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.update.AddValueUpdate;
import com.yahoo.document.update.ArithmeticValueUpdate;
import com.yahoo.document.update.ArithmeticValueUpdate.Operator;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.ClearValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.tensor.Tensor;
import com.yahoo.text.Utf8;
import org.apache.commons.codec.binary.Base64;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.internal.matchers.Contains;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.yahoo.test.json.JsonTestHelper.inputJson;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Basic test of JSON streams to Vespa document instances.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class JsonReaderTestCase {
    DocumentTypeManager types;
    JsonFactory parserFactory;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        parserFactory = new JsonFactory();
        types = new DocumentTypeManager();
        {
            DocumentType x = new DocumentType("smoke");
            x.addField(new Field("something", DataType.STRING));
            x.addField(new Field("nalle", DataType.STRING));
            x.addField(new Field("int1", DataType.INT));
            types.registerDocumentType(x);
        }
        {
            DocumentType x = new DocumentType("mirrors");
            StructDataType woo = new StructDataType("woo");
            woo.addField(new Field("sandra", DataType.STRING));
            woo.addField(new Field("cloud", DataType.STRING));
            x.addField(new Field("skuggsjaa", woo));
            types.registerDocumentType(x);
        }
        {
            DocumentType x = new DocumentType("testarray");
            DataType d = new ArrayDataType(DataType.STRING);
            x.addField(new Field("actualarray", d));
            types.registerDocumentType(x);
        }
        {
            DocumentType x = new DocumentType("testset");
            DataType d = new WeightedSetDataType(DataType.STRING, true, true);
            x.addField(new Field("actualset", d));
            types.registerDocumentType(x);
        }
        {
            DocumentType x = new DocumentType("testmap");
            DataType d = new MapDataType(DataType.STRING, DataType.STRING);
            x.addField(new Field("actualmap", d));
            types.registerDocumentType(x);
        }
        {
            DocumentType x = new DocumentType("testraw");
            DataType d = DataType.RAW;
            x.addField(new Field("actualraw", d));
            types.registerDocumentType(x);
        }
        {
            DocumentType x = new DocumentType("testMapStringToArrayOfInt");
            DataType value = new ArrayDataType(DataType.INT);
            DataType d = new MapDataType(DataType.STRING, value);
            x.addField(new Field("actualMapStringToArrayOfInt", d));
            types.registerDocumentType(x);
        }
        {
            DocumentType x = new DocumentType("testsinglepos");
            DataType d = PositionDataType.INSTANCE;
            x.addField(new Field("singlepos", d));
            types.registerDocumentType(x);
        }
        {
            DocumentType x = new DocumentType("testtensor");
            x.addField(new Field("tensorfield", DataType.TENSOR));
            types.registerDocumentType(x);
        }
        {
            DocumentType x = new DocumentType("testpredicate");
            x.addField(new Field("boolean", DataType.PREDICATE));
            types.registerDocumentType(x);
        }
        {
            DocumentType x = new DocumentType("testint");
            x.addField(new Field("integerfield", DataType.INT));
            types.registerDocumentType(x);
        }
    }

    @After
    public void tearDown() throws Exception {
        types = null;
        parserFactory = null;
        exception = ExpectedException.none();
    }

    @Test
    public final void readSingleDocumentPut() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:smoke::whee\","
                        + " \"fields\": { \"something\": \"smoketest\","
                        + " \"nalle\": \"bamse\"}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        DocumentPut put = (DocumentPut) r.readSingleDocument(JsonReader.SupportedOperation.PUT, "id:unittest:smoke::whee");
        smokeTestDoc(put.getDocument());
    }

    @Test
    public final void readSingleDocumentUpdate() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"update\": \"id:unittest:smoke::whee\","
                        + " \"fields\": { \"something\": {"
                        + " \"assign\": \"orOther\" }}" + " }"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        DocumentUpdate doc = (DocumentUpdate) r.readSingleDocument(JsonReader.SupportedOperation.UPDATE, "id:unittest:smoke::whee");
        FieldUpdate f = doc.getFieldUpdate("something");
        assertEquals(1, f.size());
        assertTrue(f.getValueUpdate(0) instanceof AssignValueUpdate);
    }

    @Test
    public final void readClearField() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"update\": \"id:unittest:smoke::whee\","
                        + " \"fields\": { \"int1\": {"
                        + " \"assign\": null }}" + " }"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        DocumentUpdate doc = (DocumentUpdate) r.readSingleDocument(JsonReader.SupportedOperation.UPDATE, "id:unittest:smoke::whee");
        FieldUpdate f = doc.getFieldUpdate("int1");
        assertEquals(1, f.size());
        assertTrue(f.getValueUpdate(0) instanceof ClearValueUpdate);
        assertNull(f.getValueUpdate(0).getValue());
    }


    @Test
    public final void smokeTest() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:smoke::whee\","
                        + " \"fields\": { \"something\": \"smoketest\","
                        + " \"nalle\": \"bamse\"}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        smokeTestDoc(put.getDocument());
    }

    @Test
    public final void docIdLookaheadTest() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{"
                        + " \"fields\": { \"something\": \"smoketest\","
                        + " \"nalle\": \"bamse\"},"
                        + "\"put\": \"id:unittest:smoke::whee\""
                        + "}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        smokeTestDoc(put.getDocument());
    }


    @Test
    public final void emptyDocTest() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:smoke::whee\","
                        + " \"fields\": {}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        assertEquals("id:unittest:smoke::whee", parseInfo.documentId.toString());
    }

    @Test
    public final void testStruct() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:mirrors::whee\","
                        + " \"fields\": { "
                        + "\"skuggsjaa\": {"
                        + "\"sandra\": \"person\","
                        + " \"cloud\": \"another person\"}}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        Document doc = put.getDocument();
        FieldValue f = doc.getFieldValue(doc.getField("skuggsjaa"));
        assertSame(Struct.class, f.getClass());
        Struct s = (Struct) f;
        assertEquals("person", ((StringFieldValue) s.getFieldValue("sandra")).getString());
    }

    @Test
    public final void testUpdateArray() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"update\": \"id:unittest:testarray::whee\","
                        + " \"fields\": { " + "\"actualarray\": {"
                        + " \"add\": ["
                        + " \"person\","
                        + " \"another person\"]}}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentUpdate doc = new DocumentUpdate(docType, parseInfo.documentId);
        r.readUpdate(doc);
        checkSimpleArrayAdd(doc);
    }

    @Test
    public final void testUpdateWeighted() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"update\": \"id:unittest:testset::whee\","
                        + " \"fields\": { " + "\"actualset\": {"
                        + " \"add\": {"
                        + " \"person\": 37,"
                        + " \"another person\": 41}}}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentUpdate doc = new DocumentUpdate(docType, parseInfo.documentId);
        r.readUpdate(doc);
        Map<String, Integer> weights = new HashMap<>();
        FieldUpdate x = doc.getFieldUpdate("actualset");
        for (ValueUpdate<?> v : x.getValueUpdates()) {
            AddValueUpdate adder = (AddValueUpdate) v;
            final String s = ((StringFieldValue) adder.getValue()).getString();
            weights.put(s, adder.getWeight());
        }
        assertEquals(2, weights.size());
        final String o = "person";
        final String o2 = "another person";
        assertTrue(weights.containsKey(o));
        assertTrue(weights.containsKey(o2));
        assertEquals(Integer.valueOf(37), weights.get(o));
        assertEquals(Integer.valueOf(41), weights.get(o2));
    }

    @Test
    public final void testUpdateMatch() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"update\": \"id:unittest:testset::whee\","
                        + " \"fields\": { " + "\"actualset\": {"
                        + " \"match\": {"
                        + " \"element\": \"person\","
                        + " \"increment\": 13}}}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentUpdate doc = new DocumentUpdate(docType, parseInfo.documentId);

        r.readUpdate(doc);
        Map<String, Tuple2<Number, String>> matches = new HashMap<>();
        FieldUpdate x = doc.getFieldUpdate("actualset");
        for (ValueUpdate<?> v : x.getValueUpdates()) {
            MapValueUpdate adder = (MapValueUpdate) v;
            final String key = ((StringFieldValue) adder.getValue())
                    .getString();
            String op = ((ArithmeticValueUpdate) adder.getUpdate())
                    .getOperator().toString();
            Number n = ((ArithmeticValueUpdate) adder.getUpdate()).getOperand();
            matches.put(key, new Tuple2<>(n, op));
        }
        assertEquals(1, matches.size());
        final String o = "person";
        assertEquals("ADD", matches.get(o).second);
        assertEquals(Double.valueOf(13), matches.get(o).first);
    }

    @SuppressWarnings({ "cast", "unchecked", "rawtypes" })
    @Test
    public final void testArithmeticOperators() {
        Tuple2[] operations = new Tuple2[] {
                new Tuple2<String, Operator>(JsonReader.UPDATE_DECREMENT,
                        ArithmeticValueUpdate.Operator.SUB),
                new Tuple2<String, Operator>(JsonReader.UPDATE_DIVIDE,
                        ArithmeticValueUpdate.Operator.DIV),
                new Tuple2<String, Operator>(JsonReader.UPDATE_INCREMENT,
                        ArithmeticValueUpdate.Operator.ADD),
                new Tuple2<String, Operator>(JsonReader.UPDATE_MULTIPLY,
                        ArithmeticValueUpdate.Operator.MUL) };
        for (Tuple2<String, Operator> operator : operations) {
            InputStream rawDoc = new ByteArrayInputStream(
                    Utf8.toBytes("{\"update\": \"id:unittest:testset::whee\","
                            + " \"fields\": { " + "\"actualset\": {"
                            + " \"match\": {" + " \"element\": \"person\","
                            + " \"" + (String) operator.first + "\": 13}}}}"));

            JsonReader r = new JsonReader(types, rawDoc, parserFactory);
            JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
            DocumentType docType = r.readDocumentType(parseInfo.documentId);
            DocumentUpdate doc = new DocumentUpdate(docType, parseInfo.documentId);

            r.readUpdate(doc);
            Map<String, Tuple2<Number, Operator>> matches = new HashMap<>();
            FieldUpdate x = doc.getFieldUpdate("actualset");
            for (ValueUpdate v : x.getValueUpdates()) {
                MapValueUpdate adder = (MapValueUpdate) v;
                final String key = ((StringFieldValue) adder.getValue())
                        .getString();
                Operator op = ((ArithmeticValueUpdate) adder
                        .getUpdate()).getOperator();
                Number n = ((ArithmeticValueUpdate) adder.getUpdate())
                        .getOperand();
                matches.put(key, new Tuple2<>(n, op));
            }
            assertEquals(1, matches.size());
            final String o = "person";
            assertSame(operator.second, matches.get(o).second);
            assertEquals(Double.valueOf(13), matches.get(o).first);
        }
    }

    @SuppressWarnings("rawtypes")
    @Test
    public final void testArrayIndexing() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"update\": \"id:unittest:testarray::whee\","
                        + " \"fields\": { " + "\"actualarray\": {"
                        + " \"match\": {"
                        + " \"element\": 3,"
                        + " \"assign\": \"nalle\"}}}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentUpdate doc = new DocumentUpdate(docType, parseInfo.documentId);

        r.readUpdate(doc);
        Map<Number, String> matches = new HashMap<>();
        FieldUpdate x = doc.getFieldUpdate("actualarray");
        for (ValueUpdate v : x.getValueUpdates()) {
            MapValueUpdate adder = (MapValueUpdate) v;
            final Number key = ((IntegerFieldValue) adder.getValue())
                    .getNumber();
            String op = ((StringFieldValue) ((AssignValueUpdate) adder.getUpdate())
                    .getValue()).getString();
            matches.put(key, op);
        }
        assertEquals(1, matches.size());
        Number n = Integer.valueOf(3);
        assertEquals("nalle", matches.get(n));
    }

    @Test
    public final void testDocumentRemove() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"remove\": \"id:unittest:smoke::whee\""
                        + " }}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        DocumentType docType = r.readDocumentType(new DocumentId("id:unittest:smoke::whee"));
        assertEquals("smoke", docType.getName());
    }

    @Test
    public final void testWeightedSet() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:testset::whee\","
                        + " \"fields\": { \"actualset\": {"
                        + " \"nalle\": 2,"
                        + " \"tralle\": 7 }}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        Document doc = put.getDocument();
        FieldValue f = doc.getFieldValue(doc.getField("actualset"));
        assertSame(WeightedSet.class, f.getClass());
        WeightedSet<?> w = (WeightedSet<?>) f;
        assertEquals(2, w.size());
        assertEquals(new Integer(2), w.get(new StringFieldValue("nalle")));
        assertEquals(new Integer(7), w.get(new StringFieldValue("tralle")));
    }

    @Test
    public final void testArray() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:testarray::whee\","
                        + " \"fields\": { \"actualarray\": ["
                        + " \"nalle\","
                        + " \"tralle\"]}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        Document doc = put.getDocument();
        FieldValue f = doc.getFieldValue(doc.getField("actualarray"));
        assertSame(Array.class, f.getClass());
        Array<?> a = (Array<?>) f;
        assertEquals(2, a.size());
        assertEquals(new StringFieldValue("nalle"), a.get(0));
        assertEquals(new StringFieldValue("tralle"), a.get(1));
    }

    @Test
    public final void testMap() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:testmap::whee\","
                        + " \"fields\": { \"actualmap\": ["
                        + " { \"key\": \"nalle\", \"value\": \"kalle\"},"
                        + " { \"key\": \"tralle\", \"value\": \"skalle\"} ]}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        Document doc = put.getDocument();
        FieldValue f = doc.getFieldValue(doc.getField("actualmap"));
        assertSame(MapFieldValue.class, f.getClass());
        MapFieldValue<?, ?> m = (MapFieldValue<?, ?>) f;
        assertEquals(2, m.size());
        assertEquals(new StringFieldValue("kalle"), m.get(new StringFieldValue("nalle")));
        assertEquals(new StringFieldValue("skalle"), m.get(new StringFieldValue("tralle")));
    }

    @Test
    public final void testPositionPositive() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:testsinglepos::bamf\","
                        + " \"fields\": { \"singlepos\": \"N63.429722;E10.393333\" }}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        Document doc = put.getDocument();
        FieldValue f = doc.getFieldValue(doc.getField("singlepos"));
        assertSame(Struct.class, f.getClass());
        assertEquals(10393333, PositionDataType.getXValue(f).getInteger());
        assertEquals(63429722, PositionDataType.getYValue(f).getInteger());
    }

    @Test
    public final void testPositionNegative() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:testsinglepos::bamf\","
                        + " \"fields\": { \"singlepos\": \"W46.63;S23.55\" }}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        Document doc = put.getDocument();
        FieldValue f = doc.getFieldValue(doc.getField("singlepos"));
        assertSame(Struct.class, f.getClass());
        assertEquals(-46630000, PositionDataType.getXValue(f).getInteger());
        assertEquals(-23550000, PositionDataType.getYValue(f).getInteger());
    }

    @Test
    public final void testRaw() {
        String stuff = new String(new JsonStringEncoder().quoteAsString(new Base64().encodeToString(Utf8.toBytes("smoketest"))));
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:testraw::whee\","
                        + " \"fields\": { \"actualraw\": \""
                        + stuff
                        + "\""
                        + " }}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        Document doc = put.getDocument();
        FieldValue f = doc.getFieldValue(doc.getField("actualraw"));
        assertSame(Raw.class, f.getClass());
        Raw s = (Raw) f;
        ByteBuffer b = s.getByteBuffer();
        assertEquals("smoketest", Utf8.toString(b));
    }

    @Test
    public final void testMapStringToArrayOfInt() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:testMapStringToArrayOfInt::whee\","
                        + " \"fields\": { \"actualMapStringToArrayOfInt\": ["
                        + "{ \"key\": \"bamse\", \"value\": [1, 2, 3] }"
                        + "]}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        Document doc = put.getDocument();
        FieldValue f = doc.getFieldValue("actualMapStringToArrayOfInt");
        assertSame(MapFieldValue.class, f.getClass());
        MapFieldValue<?, ?> m = (MapFieldValue<?, ?>) f;
        Array<?> a = (Array<?>) m.get(new StringFieldValue("bamse"));
        assertEquals(3, a.size());
        assertEquals(new IntegerFieldValue(1), a.get(0));
        assertEquals(new IntegerFieldValue(2), a.get(1));
        assertEquals(new IntegerFieldValue(3), a.get(2));
    }

    @Test
    public final void testAssignToString() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"update\": \"id:unittest:smoke::whee\","
                        + " \"fields\": { \"something\": {"
                        + " \"assign\": \"orOther\" }}" + " }"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentUpdate doc = new DocumentUpdate(docType, parseInfo.documentId);
        r.readUpdate(doc);
        FieldUpdate f = doc.getFieldUpdate("something");
        assertEquals(1, f.size());
        AssignValueUpdate a = (AssignValueUpdate) f.getValueUpdate(0);
        assertEquals(new StringFieldValue("orOther"), a.getValue());
    }

    @Test
    public final void testAssignToArray() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"update\": \"id:unittest:testMapStringToArrayOfInt::whee\","
                        + " \"fields\": { \"actualMapStringToArrayOfInt\": {"
                        + " \"assign\": ["
                        + "{ \"key\": \"bamse\", \"value\": [1, 2, 3] }"
                        + "]}}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentUpdate doc = new DocumentUpdate(docType, parseInfo.documentId);
        r.readUpdate(doc);
        FieldUpdate f = doc.getFieldUpdate("actualMapStringToArrayOfInt");
        assertEquals(1, f.size());
        AssignValueUpdate assign = (AssignValueUpdate) f.getValueUpdate(0);
        MapFieldValue<?, ?> m = (MapFieldValue<?, ?>) assign.getValue();
        Array<?> a = (Array<?>) m.get(new StringFieldValue("bamse"));
        assertEquals(3, a.size());
        assertEquals(new IntegerFieldValue(1), a.get(0));
        assertEquals(new IntegerFieldValue(2), a.get(1));
        assertEquals(new IntegerFieldValue(3), a.get(2));
    }

    @Test
    public final void testAssignToWeightedSet() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"update\": \"id:unittest:testset::whee\","
                        + " \"fields\": { " + "\"actualset\": {"
                        + " \"assign\": {"
                        + " \"person\": 37,"
                        + " \"another person\": 41}}}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentUpdate doc = new DocumentUpdate(docType, parseInfo.documentId);
        r.readUpdate(doc);
        FieldUpdate x = doc.getFieldUpdate("actualset");
        assertEquals(1, x.size());
        AssignValueUpdate assign = (AssignValueUpdate) x.getValueUpdate(0);
        WeightedSet<?> w = (WeightedSet<?>) assign.getValue();
        assertEquals(2, w.size());
        assertEquals(new Integer(37), w.get(new StringFieldValue("person")));
        assertEquals(new Integer(41), w.get(new StringFieldValue("another person")));
    }


    @Test
    public final void testCompleteFeed() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("[{\"put\": \"id:unittest:smoke::whee\","
                        + " \"fields\": { \"something\": \"smoketest\","
                        + " \"nalle\": \"bamse\"}}" + ", "
                        + "{\"update\": \"id:unittest:testarray::whee\","
                        + " \"fields\": { " + "\"actualarray\": {"
                        + " \"add\": [" + " \"person\","
                        + " \"another person\"]}}}" + ", "
                        + "{\"remove\": \"id:unittest:smoke::whee\"}]"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);

        controlBasicFeed(r);
    }

    @Test
    public final void testCompleteFeedWithCreateAndCondition() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("[{\"put\": \"id:unittest:smoke::whee\","
                        + " \"fields\": { \"something\": \"smoketest\","
                        + " \"nalle\": \"bamse\"}}" + ", "
                        + "{"
                        +  "\"condition\":\"bla\","
                        +  "\"update\": \"id:unittest:testarray::whee\","
                        + " \"create\":true,"
                        + " \"fields\": { " + "\"actualarray\": {"
                        + " \"add\": [" + " \"person\","
                        + " \"another person\"]}}}" + ", "
                        + "{\"remove\": \"id:unittest:smoke::whee\"}]"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);

        DocumentOperation d = r.next();
        Document doc = ((DocumentPut) d).getDocument();
        smokeTestDoc(doc);

        d = r.next();
        DocumentUpdate update = (DocumentUpdate) d;
        checkSimpleArrayAdd(update);
        assertThat(update.getCreateIfNonExistent(), is(true));
        assertThat(update.getCondition().getSelection(), is("bla"));

        d = r.next();
        DocumentRemove remove = (DocumentRemove) d;
        assertEquals("smoke", remove.getId().getDocType());

        assertNull(r.next());
    }

    @Test
    public final void testUpdateWithConditionAndCreateInDifferentOrdering() {
        final int  documentsCreated = 106;
        List<String> parts = Arrays.asList(
                "\"condition\":\"bla\"",
                "\"update\": \"id:unittest:testarray::whee\"",
                " \"fields\": { " + "\"actualarray\": { \"add\": [" + " \"person\",\"another person\"]}}",
                " \"create\":true");
        final Random random = new Random(42);
        StringBuilder documents = new StringBuilder("[");
        for (int x = 0; x < documentsCreated; x++) {
            Collections.shuffle(parts, random);
            documents.append("{").append(Joiner.on(",").join(parts)).append("}");
            if (x < documentsCreated -1) {
                documents.append(",");
            }
        }
        documents.append("]");
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes(documents.toString()));

        JsonReader r = new JsonReader(types, rawDoc, parserFactory);

        for (int x = 0; x < documentsCreated; x++) {
            DocumentUpdate update = (DocumentUpdate) r.next();
            checkSimpleArrayAdd(update);
            assertThat(update.getCreateIfNonExistent(), is(true));
            assertThat(update.getCondition().getSelection(), is("bla"));

        }

        assertNull(r.next());
    }


    @Test(expected=RuntimeException.class)
    public final void testCreateIfNonExistentInPut() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("[{"
                        + " \"create\":true,"
                        + " \"fields\": { \"something\": \"smoketest\","
                        + " \"nalle\": \"bamse\"},"
                        + "\"put\": \"id:unittest:smoke::whee\""
                        + "}]"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        r.next();
    }

    @Test
    public final void testCompleteFeedWithIdAfterFields() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("[{"
                        + " \"fields\": { \"something\": \"smoketest\","
                        + " \"nalle\": \"bamse\"},"
                        + "\"put\": \"id:unittest:smoke::whee\""
                        + "}" + ", "
                        + "{"
                        + " \"fields\": { " + "\"actualarray\": {"
                        + " \"add\": [" + " \"person\","
                        + " \"another person\"]}},"
                        + "\"update\": \"id:unittest:testarray::whee\""
                        + "}" + ", "
                        + "{\"remove\": \"id:unittest:smoke::whee\"}]"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);

        controlBasicFeed(r);
    }

    protected void controlBasicFeed(JsonReader r) {
        DocumentOperation d = r.next();
        Document doc = ((DocumentPut) d).getDocument();
        smokeTestDoc(doc);

        d = r.next();
        DocumentUpdate update = (DocumentUpdate) d;
        checkSimpleArrayAdd(update);

        d = r.next();
        DocumentRemove remove = (DocumentRemove) d;
        assertEquals("smoke", remove.getId().getDocType());

        assertNull(r.next());
    }


    @Test
    public final void testCompleteFeedWithEmptyDoc() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("[{\"put\": \"id:unittest:smoke::whee\","
                        + " \"fields\": {}}" + ", "
                        + "{\"update\": \"id:unittest:testarray::whee\","
                        + " \"fields\": {}}" + ", "
                        + "{\"remove\": \"id:unittest:smoke::whee\"}]"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);


        DocumentOperation d = r.next();
        Document doc = ((DocumentPut) d).getDocument();
        assertEquals("smoke", doc.getId().getDocType());

        d = r.next();
        DocumentUpdate update = (DocumentUpdate) d;
        assertEquals("testarray", update.getId().getDocType());

        d = r.next();
        DocumentRemove remove = (DocumentRemove) d;
        assertEquals("smoke", remove.getId().getDocType());

        assertNull(r.next());

    }

    private void checkSimpleArrayAdd(DocumentUpdate update) {
        Set<String> toAdd = new HashSet<>();
        FieldUpdate x = update.getFieldUpdate("actualarray");
        for (ValueUpdate<?> v : x.getValueUpdates()) {
            AddValueUpdate adder = (AddValueUpdate) v;
            toAdd.add(((StringFieldValue) adder.getValue()).getString());
        }
        assertEquals(2, toAdd.size());
        assertTrue(toAdd.contains("person"));
        assertTrue(toAdd.contains("another person"));
    }

    private void smokeTestDoc(Document doc) {
        FieldValue f = doc.getFieldValue(doc.getField("nalle"));
        assertSame(StringFieldValue.class, f.getClass());
        StringFieldValue s = (StringFieldValue) f;
        assertEquals("bamse", s.getString());
    }

    @Test
    public final void misspelledFieldTest() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"put\": \"id:unittest:smoke::whee\","
                        + " \"fields\": { \"smething\": \"smoketest\","
                        + " \"nalle\": \"bamse\"}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        exception.expect(NullPointerException.class);
        exception.expectMessage("Could not get field \"smething\" in the structure of type \"smoke\".");
        r.readPut(put);
    }

    @Test
    public final void feedWithBasicErrorTest() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("["
                        + "  { \"put\": \"id:test:smoke::0\", \"fields\": { \"something\": \"foo\" } },"
                        + "  { \"put\": \"id:test:smoke::1\", \"fields\": { \"something\": \"foo\" } },"
                        + "  { \"put\": \"id:test:smoke::2\", \"fields\": { \"something\": \"foo\" } },"
                        + "]"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        exception.expect(RuntimeException.class);
        exception.expectMessage("JsonParseException");
        while (r.next() != null);
    }

    @Test
    public final void idAsAliasForPutTest() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("{\"id\": \"id:unittest:smoke::whee\","
                        + " \"fields\": { \"something\": \"smoketest\","
                        + " \"nalle\": \"bamse\"}}"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        JsonReader.DocumentParseInfo parseInfo = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(parseInfo.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, parseInfo.documentId));
        r.readPut(put);
        smokeTestDoc(put.getDocument());
    }

    private void testFeedWithTestAndSetCondition(String jsonDoc) {
        final ByteArrayInputStream parseInfoDoc = new ByteArrayInputStream(Utf8.toBytes(jsonDoc));
        final JsonReader reader = new JsonReader(types, parseInfoDoc, parserFactory);
        final int NUM_OPERATIONS_IN_FEED = 3;

        for (int i = 0; i < NUM_OPERATIONS_IN_FEED; i++) {
            DocumentOperation operation = reader.next();

            assertTrue("A test and set condition should be present",
                    operation.getCondition().isPresent());

            assertEquals("DocumentOperation's test and set condition should be equal to the one in the JSON feed",
                    "smoke.something == \"smoketest\"",
                    operation.getCondition().getSelection());
        }

        assertNull(reader.next());
    }

    @Test
    public final void testFeedWithTestAndSetConditionOrderingOne() {
        testFeedWithTestAndSetCondition(
                inputJson("[",
                        "      {",
                        "          'put': 'id:unittest:smoke::whee',",
                        "          'condition': 'smoke.something == \\'smoketest\\'',",
                        "          'fields': {",
                        "              'something': 'smoketest',",
                        "              'nalle': 'bamse'",
                        "          }",
                        "      },",
                        "      {",
                        "          'update': 'id:unittest:testarray::whee',",
                        "          'condition': 'smoke.something == \\'smoketest\\'',",
                        "          'fields': {",
                        "              'actualarray': {",
                        "                  'add': [",
                        "                      'person',",
                        "                      'another person'",
                        "                   ]",
                        "              }",
                        "          }",
                        "      },",
                        "      {",
                        "          'remove': 'id:unittest:smoke::whee',",
                        "          'condition': 'smoke.something == \\'smoketest\\''",
                        "      }",
                        "]"
                ));
    }

    @Test
    public final void testFeedWithTestAndSetConditionOrderingTwo() {
        testFeedWithTestAndSetCondition(
                inputJson("[",
                        "      {",
                        "          'condition': 'smoke.something == \\'smoketest\\'',",
                        "          'put': 'id:unittest:smoke::whee',",
                        "          'fields': {",
                        "              'something': 'smoketest',",
                        "              'nalle': 'bamse'",
                        "          }",
                        "      },",
                        "      {",
                        "          'condition': 'smoke.something == \\'smoketest\\'',",
                        "          'update': 'id:unittest:testarray::whee',",
                        "          'fields': {",
                        "              'actualarray': {",
                        "                  'add': [",
                        "                      'person',",
                        "                      'another person'",
                        "                   ]",
                        "              }",
                        "          }",
                        "      },",
                        "      {",
                        "          'condition': 'smoke.something == \\'smoketest\\'',",
                        "          'remove': 'id:unittest:smoke::whee'",
                        "      }",
                        "]"
                ));
    }

    @Test
    public final void testFeedWithTestAndSetConditionOrderingThree() {
        testFeedWithTestAndSetCondition(
                inputJson("[",
                        "      {",
                        "          'put': 'id:unittest:smoke::whee',",
                        "          'fields': {",
                        "              'something': 'smoketest',",
                        "              'nalle': 'bamse'",
                        "          },",
                        "          'condition': 'smoke.something == \\'smoketest\\''",
                        "      },",
                        "      {",
                        "          'update': 'id:unittest:testarray::whee',",
                        "          'fields': {",
                        "              'actualarray': {",
                        "                  'add': [",
                        "                      'person',",
                        "                      'another person'",
                        "                   ]",
                        "              }",
                        "          },",
                        "          'condition': 'smoke.something == \\'smoketest\\''",
                        "      },",
                        "      {",
                        "          'remove': 'id:unittest:smoke::whee',",
                        "          'condition': 'smoke.something == \\'smoketest\\''",
                        "      }",
                        "]"
                ));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFieldAfterFieldsFieldShouldFailParse() {
        final String jsonData = inputJson(
                "[",
                "      {",
                "          'put': 'id:unittest:smoke::whee',",
                "          'fields': {",
                "              'something': 'smoketest',",
                "              'nalle': 'bamse'",
                "          },",
                "          'bjarne': 'stroustrup'",
                "      }",
                "]");

        new JsonReader(types, jsonToInputStream(jsonData), parserFactory).next();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFieldBeforeFieldsFieldShouldFailParse() {
        final String jsonData = inputJson(
                "[",
                "      {",
                "          'update': 'id:unittest:testarray::whee',",
                "          'what is this': 'nothing to see here',",
                "          'fields': {",
                "              'actualarray': {",
                "                  'add': [",
                "                      'person',",
                "                      'another person'",
                "                   ]",
                "              }",
                "          }",
                "      }",
                "]");

        new JsonReader(types, jsonToInputStream(jsonData), parserFactory).next();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFieldWithoutFieldsFieldShouldFailParse() {
        final String jsonData = inputJson(
                "[",
                "      {",
                "          'remove': 'id:unittest:smoke::whee',",
                "          'what is love': 'baby, do not hurt me... much'",
                "      }",
                "]");

        new JsonReader(types, jsonToInputStream(jsonData), parserFactory).next();
    }

    static ByteArrayInputStream jsonToInputStream(String json) {
        return new ByteArrayInputStream(Utf8.toBytes(json));
    }

    @Test
    public void testParsingWithoutTensorField() {
        Document doc = createPutWithoutTensor().getDocument();
        assertEquals("testtensor", doc.getId().getDocType());
        assertEquals("id:unittest:testtensor::0", doc.getId().toString());
        TensorFieldValue fieldValue = (TensorFieldValue)doc.getFieldValue(doc.getField("tensorfield"));
        assertNull(fieldValue);
    }

    @Test
    public void testParsingOfEmptyTensor() {
        assertTensorField("{}", createPutWithTensor("{}"));
    }

    @Test
    public void testParsingOfTensorWithEmptyDimensions() {
        assertTensorField("{}",
                createPutWithTensor("{ "
                        + "  \"dimensions\": [] "
                        + "}"));
    }

    @Test
    public void testParsingOfTensorWithEmptyCells() {
        assertTensorField("{}",
                createPutWithTensor("{ "
                        + "  \"cells\": [] "
                        + "}"));
    }

    @Test
    public void testParsingOfTensorWithCells() {
        assertTensorField("{{x:a,y:b}:2.0,{x:c,y:b}:3.0}}",
                createPutWithTensor("{ "
                        + "  \"cells\": [ "
                        + "    { \"address\": { \"x\": \"a\", \"y\": \"b\" }, "
                        + "      \"value\": 2.0 }, "
                        + "    { \"address\": { \"x\": \"c\", \"y\": \"b\" }, "
                        + "      \"value\": 3.0 } "
                        + "  ]"
                        + "}"));
    }

    @Test
    public void testParsingOfTensorWithSingleCellInDifferentJsonOrder() {
        assertTensorField("{{x:a,y:b}:2.0}",
                createPutWithTensor("{ "
                        + "  \"cells\": [ "
                        + "    { \"value\": 2.0, "
                        + "      \"address\": { \"x\": \"a\", \"y\": \"b\" } } "
                        + "  ]"
                        + "}"));
    }

    @Test
    public void testParsingOfTensorWithSingleCellWithoutAddress() {
        assertTensorField("{{}:2.0}",
                createPutWithTensor("{ "
                        + "  \"cells\": [ "
                        + "    { \"value\": 2.0 } "
                        + "  ]"
                        + "}"));
    }

    @Test
    public void testParsingOfTensorWithSingleCellWithoutValue() {
        assertTensorField("{{x:a}:0.0}",
                createPutWithTensor("{ "
                        + "  \"cells\": [ "
                        + "    { \"address\": { \"x\": \"a\" } } "
                        + "  ]"
                        + "}"));
    }

    @Test
    public void testAssignUpdateOfEmptyTensor() {
        assertTensorAssignUpdate("{}", createAssignUpdateWithTensor("{}"));
    }

    @Test
    public void testAssignUpdateOfNullTensor() {
        ClearValueUpdate clearUpdate = (ClearValueUpdate) getTensorField(createAssignUpdateWithTensor(null)).getValueUpdate(0);
        assertTrue(clearUpdate != null);
        assertTrue(clearUpdate.getValue() == null);
    }

    @Test
    public void testAssignUpdateOfTensorWithCells() {
        assertTensorAssignUpdate("{{x:a,y:b}:2.0,{x:c,y:b}:3.0}}",
                createAssignUpdateWithTensor("{ "
                        + "  \"cells\": [ "
                        + "    { \"address\": { \"x\": \"a\", \"y\": \"b\" }, "
                        + "      \"value\": 2.0 }, "
                        + "    { \"address\": { \"x\": \"c\", \"y\": \"b\" }, "
                        + "      \"value\": 3.0 } "
                        + "  ]"
                        + "}"));
    }

    @Test
    public void require_that_parser_propagates_datatype_parser_errors_predicate() {
        assertParserErrorMatches(
                "Error in document 'id:unittest:testpredicate::0' - could not parse field 'boolean' of type 'predicate': " +
                        "line 1:10 no viable alternative at character '>'",

                "[",
                "      {",
                "          'fields': {",
                "              'boolean': 'timestamp > 9000'",
                "          },",
                "          'put': 'id:unittest:testpredicate::0'",
                "      }",
                "]"
        );
    }

    @Test
    public void require_that_parser_propagates_datatype_parser_errors_string_as_int() {
        assertParserErrorMatches(
                "Error in document 'id:unittest:testint::0' - could not parse field 'integerfield' of type 'int': " +
                        "For input string: \" 1\"",

                "[",
                "      {",
                "          'fields': {",
                "              'integerfield': ' 1'",
                "          },",
                "          'put': 'id:unittest:testint::0'",
                "      }",
                "]"
        );
    }

    @Test
    public void require_that_parser_propagates_datatype_parser_errors_overflowing_int() {
        assertParserErrorMatches(
                "Error in document 'id:unittest:testint::0' - could not parse field 'integerfield' of type 'int': " +
                        "For input string: \"281474976710656\"",

                "[",
                "      {",
                "          'fields': {",
                "              'integerfield': 281474976710656",
                "          },",
                "          'put': 'id:unittest:testint::0'",
                "      }",
                "]"
        );
    }

    @Test
    public void requireThatUnknownDocTypeThrowsIllegalArgumentException() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(new Contains("Document type walrus does not exist"));

        final String jsonData = inputJson(
                "[",
                "      {",
                "          'put': 'id:ns:walrus::walrus1',",
                "          'fields': {",
                "              'aField': 42",
                "          }",
                "      }",
                "]");

        new JsonReader(types, jsonToInputStream(jsonData), parserFactory).next();
    }

    private static final String TENSOR_DOC_ID = "id:unittest:testtensor::0";

    private DocumentPut createPutWithoutTensor() {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("[ { \"put\": \"" + TENSOR_DOC_ID + "\", \"fields\": { } } ]"));
        JsonReader reader = new JsonReader(types, rawDoc, parserFactory);
        return (DocumentPut) reader.next();
    }

    private DocumentPut createPutWithTensor(String inputTensor) {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("["
                        + "  { \"put\": \"" + TENSOR_DOC_ID + "\", \"fields\": { \"tensorfield\": "
                        + inputTensor
                        + "  }}"
                        + "]"));
        JsonReader reader = new JsonReader(types, rawDoc, parserFactory);
        return (DocumentPut) reader.next();
    }

    private DocumentUpdate createAssignUpdateWithTensor(String inputTensor) {
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("[ { \"update\": \"" + TENSOR_DOC_ID + "\", \"fields\": { \"tensorfield\": {"
                        + "\"assign\": " + (inputTensor != null ? inputTensor : "null") + " } } } ]"));
        JsonReader reader = new JsonReader(types, rawDoc, parserFactory);
        return (DocumentUpdate) reader.next();
    }

    private static void assertTensorField(String expectedTensor, DocumentPut put) {
        final Document doc = put.getDocument();
        assertEquals("testtensor", doc.getId().getDocType());
        assertEquals(TENSOR_DOC_ID, doc.getId().toString());
        TensorFieldValue fieldValue = (TensorFieldValue)doc.getFieldValue(doc.getField("tensorfield"));
        assertEquals(Tensor.from(expectedTensor), fieldValue.getTensor().get());
    }

    private static void assertTensorAssignUpdate(String expectedTensor, DocumentUpdate update) {
        assertEquals("testtensor", update.getId().getDocType());
        assertEquals(TENSOR_DOC_ID, update.getId().toString());
        AssignValueUpdate assignUpdate = (AssignValueUpdate) getTensorField(update).getValueUpdate(0);
        TensorFieldValue fieldValue = (TensorFieldValue) assignUpdate.getValue();
        assertEquals(Tensor.from(expectedTensor), fieldValue.getTensor().get());
    }

    private static FieldUpdate getTensorField(DocumentUpdate update) {
        FieldUpdate fieldUpdate = update.getFieldUpdate("tensorfield");
        assertEquals(1, fieldUpdate.size());
        return fieldUpdate;
    }

    // NOTE: Do not call this method multiple times from a test method as it's using the ExpectedException rule
    private void assertParserErrorMatches(String expectedError, String... json) {
        exception.expect(JsonReaderException.class);
        exception.expectMessage(new Contains(expectedError));
        String jsonData = inputJson(json);
        new JsonReader(types, jsonToInputStream(jsonData), parserFactory).next();
    }

}
