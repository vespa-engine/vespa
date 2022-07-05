// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.ReferenceFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.internal.GeoPosType;
import com.yahoo.document.json.readers.DocumentParseInfo;
import com.yahoo.document.json.readers.VespaJsonDocumentReader;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Utf8;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yahoo.document.json.readers.MapReader.MAP_KEY;
import static com.yahoo.document.json.readers.MapReader.MAP_VALUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Functional tests for com.yahoo.document.json.JsonWriter.
 *
 * @author Steinar Knutsen
 */
public class JsonWriterTestCase {

    private static final JsonFactory parserFactory = new JsonFactory();
    private DocumentTypeManager types;

    @Before
    public void setUp() throws Exception {
        types = new DocumentTypeManager();
        registerAllTestDocumentTypes();
    }

    private void registerAllTestDocumentTypes() {
        registerSmokeDocumentType();
        registerMirrorsDocumentType();
        registerArrayDocumentType();
        registerSetDocumentType();
        registerMapDocumentType();
        registerRawDocumentType();
        registerPredicateDocumentType();
        registerMapToStringToArrayDocumentType();
        registerSinglePositionDocumentType();
        registerMultiPositionDocumentType();
        registerTensorDocumentType();
        registerIndexedTensorDocumentType();
        registerReferenceDocumentType();
    }

    private void registerReferenceDocumentType() {
        DocumentType docTypeWithRef = new DocumentType("testrefs");
        ReferenceDataType type = ReferenceDataType.createWithInferredId(types.getDocumentType("smoke"));
        docTypeWithRef.addField(new Field("ref_field", type));
        types.registerDocumentType(docTypeWithRef);
    }

    private void registerTensorDocumentType() {
        DocumentType x = new DocumentType("testtensor");
        TensorType tensorType = new TensorType.Builder().mapped("x").mapped("y").build();
        x.addField(new Field("tensorfield", new TensorDataType(tensorType)));
        types.registerDocumentType(x);
    }

    private void registerIndexedTensorDocumentType() {
        DocumentType x = new DocumentType("testindexedtensor");
        TensorType tensorType = new TensorType.Builder().indexed("x", 3).build();
        x.addField(new Field("tensorfield", new TensorDataType(tensorType)));
        types.registerDocumentType(x);
    }

    private void registerMultiPositionDocumentType() {
        DocumentType x = new DocumentType("testmultipos");
        DataType d = new ArrayDataType(PositionDataType.INSTANCE);
        x.addField(new Field("multipos", d));
        x.addField(new Field("geopos", new ArrayDataType(new GeoPosType(8))));
        types.registerDocumentType(x);
    }

    private void registerSinglePositionDocumentType() {
        DocumentType x = new DocumentType("testsinglepos");
        DataType d = PositionDataType.INSTANCE;
        x.addField(new Field("singlepos", d));
        x.addField(new Field("geopos", new GeoPosType(8)));
        types.registerDocumentType(x);
    }

    private void registerMapToStringToArrayDocumentType() {
        DocumentType x = new DocumentType("testMapStringToArrayOfInt");
        DataType value = new ArrayDataType(DataType.INT);
        DataType d = new MapDataType(DataType.STRING, value);
        x.addField(new Field("actualMapStringToArrayOfInt", d));
        types.registerDocumentType(x);
    }

    private void registerPredicateDocumentType() {
        DocumentType x = new DocumentType("testpredicate");
        DataType d = DataType.PREDICATE;
        x.addField(new Field("actualpredicate", d));
        types.registerDocumentType(x);
    }

    private void registerRawDocumentType() {
        DocumentType x = new DocumentType("testraw");
        DataType d = DataType.RAW;
        x.addField(new Field("actualraw", d));
        types.registerDocumentType(x);
    }

    private void registerMapDocumentType() {
        DocumentType x = new DocumentType("testmap");
        DataType d = new MapDataType(DataType.STRING, DataType.STRING);
        x.addField(new Field("actualmap", d));
        types.registerDocumentType(x);
    }

    private void registerSetDocumentType() {
        DocumentType x = new DocumentType("testset");
        DataType d = new WeightedSetDataType(DataType.STRING, true, true);
        x.addField(new Field("actualset", d));
        types.registerDocumentType(x);
    }

    private void registerArrayDocumentType() {
        DocumentType x = new DocumentType("testarray");
        DataType d = new ArrayDataType(DataType.STRING);
        x.addField(new Field("actualarray", d));
        types.registerDocumentType(x);
    }

    private void registerMirrorsDocumentType() {
        DocumentType x = new DocumentType("mirrors");
        StructDataType woo = new StructDataType("woo");
        woo.addField(new Field("sandra", DataType.STRING));
        woo.addField(new Field("cloud", DataType.STRING));
        x.addField(new Field("skuggsjaa", woo));
        types.registerDocumentType(x);
    }

    private void registerSmokeDocumentType() {
        DocumentType x = new DocumentType("smoke");
        x.addField(new Field("something", DataType.STRING));
        x.addField(new Field("nalle", DataType.STRING));
        types.registerDocumentType(x);
    }

    @After
    public void tearDown() throws Exception {
        types = null;
    }

    @Test
    public void smokeTest() throws IOException {
        roundTripEquality("id:unittest:smoke::whee", "{"
                + " \"something\": \"smoketest\"," + " \"nalle\": \"bamse\""
                + "}");
    }

    @Test
    public void hideEmptyStringsTest() throws IOException {
        String fields = "{"
                        + " \"something\": \"\"," + " \"nalle\": \"bamse\""
                        + "}";
        String filteredFields = "{"
                + " \"nalle\": \"bamse\""
                + "}";

        Document doc = readDocumentFromJson("id:unittest:smoke::whee", fields);
        assertEqualJson(asDocument("id:unittest:smoke::whee", filteredFields), JsonWriter.toByteArray(doc));
    }

    private void roundTripEquality(final String docId, final String fields) throws IOException {
        Document doc = readDocumentFromJson(docId, fields);
        assertEqualJson(asDocument(docId, fields), JsonWriter.toByteArray(doc));
    }

    @Test
    public void structTest() throws IOException {
        roundTripEquality("id:unittest:mirrors::whee", "{ "
                + "\"skuggsjaa\": {" + "\"sandra\": \"person\","
                + " \"cloud\": \"another person\"}}");
    }

    @Test
    public void singlePosTest() throws IOException {
        roundTripEquality("id:unittest:testsinglepos::bamf", "{ \"geopos\": { \"lat\": 60.222333, \"lng\": 10.12 } }");
    }

    @Test
    public void multiPosTest() throws IOException {
        roundTripEquality("id:unittest:testmultipos::bamf", "{ \"geopos\": [ {  \"lat\": -1.5, \"lng\": -1.5 }, {  \"lat\": 63.4, \"lng\": 10.4 }, { \"lat\": 0.0, \"lng\": 0.0 } ] }");
    }

    @Test
    public void arrayTest() throws IOException {
        roundTripEquality("id:unittest:testarray::whee", "{ \"actualarray\": ["
                + " \"nalle\"," + " \"\"," + " \"tralle\"]}");
    }

    @Test
    public void weightedSetTest() throws IOException {
        roundTripEquality("id:unittest:testset::whee", "{ \"actualset\": {"
                + " \"nalle\": 2," + " \"tralle\": 7 }}");
    }

    @Test
    public void mapTest() throws IOException {
        String fields = "{ \"actualmap\": { \"nalle\": \"kalle\", \"tralle\": \"skalle\" }}";
        String docId = "id:unittest:testmap::whee";
        Document doc = readDocumentFromJson(docId, fields);

        ObjectMapper m = new ObjectMapper();
        Map<?, ?> generated = m.readValue(JsonWriter.toByteArray(doc), Map.class);
        assertEquals(docId, generated.get("id"));
        // and from here on down there will be lots of unchecked casting and
        // other fun. This is OK here, because if the casts fail, the should and
        // will fail anyway
        Map<?, ?> inputMap = (Map<?, ?> ) m.readValue(Utf8.toBytes(fields), Map.class).get("actualmap");
        Map<?, ?> generatedMap = (Map<?, ?>) ((Map<?, ?>) generated.get("fields")).get("actualmap");
        assertEquals(inputMap, generatedMap);
    }

    @Test
    public void oldMapTest() throws IOException {
        String fields = "{ \"actualmap\": ["
                + " { \"key\": \"nalle\", \"value\": \"kalle\"},"
                + " { \"key\": \"tralle\", \"value\": \"skalle\"} ]}";
        String docId = "id:unittest:testmap::whee";
        Document doc = readDocumentFromJson(docId, fields);
        // we have to do everything by hand to check, as maps are unordered, but
        // are serialized as an ordered structure

        ObjectMapper m = new ObjectMapper();
        Map<?, ?> generated = m.readValue(JsonWriter.toByteArray(doc), Map.class);
        assertEquals(docId, generated.get("id"));
        // and from here on down there will be lots of unchecked casting and
        // other fun. This is OK here, because if the casts fail, the should and
        // will fail anyway
        List<?> inputMap = (List<?>) m.readValue(Utf8.toBytes(fields), Map.class).get("actualmap");
        Map<?, ?> generatedMap = (Map<?, ?>) ((Map<?, ?>) generated.get("fields")).get("actualmap");
        assertEquals(populateMap(inputMap), generatedMap);
    }

    // should very much blow up if the assumptions are incorrect
    @SuppressWarnings("rawtypes")
    private Map<Object, Object> populateMap(List<?> actualMap) {
        Map<Object, Object> m = new HashMap<>();
        for (Object o : actualMap) {
            Object key = ((Map) o).get(MAP_KEY);
            Object value = ((Map) o).get(MAP_VALUE);
            m.put(key, value);
        }
        return m;
    }

    @Test
    public final void raw_fields_are_emitted_as_basic_base64() throws IOException {
        // "string long enough to emit more than 76 base64 characters and which should certainly not be newline-delimited!"
        String payload = new String(
                new JsonStringEncoder().quoteAsString(
                        "c3RyaW5nIGxvbmcgZW5vdWdoIHRvIGVtaXQgbW9yZSB0aGFuIDc2IGJhc2U2NCBjaGFyYWN0ZXJzIGFuZC" +
                              "B3aGljaCBzaG91bGQgY2VydGFpbmx5IG5vdCBiZSBuZXdsaW5lLWRlbGltaXRlZCE="));

        String docId = "id:unittest:testraw::whee";

        String fields = "{ \"actualraw\": \"" + payload + "\"" + " }";
        roundTripEquality(docId, fields);
    }

    @Test
    public final void predicateTest() throws IOException {
        roundTripEquality("id:unittest:testpredicate::whee", "{ "
                + "\"actualpredicate\": \"foo in [bar]\" }");
    }

    @Test
    public final void stringToArrayOfIntMapTest() throws IOException {
        String docId = "id:unittest:testMapStringToArrayOfInt::whee";
        String fields = "{ \"actualMapStringToArrayOfInt\": { \"bamse\": [1, 2, 3] }}";
        Document doc = readDocumentFromJson(docId, fields);

        ObjectMapper m = new ObjectMapper();
        Map<?, ?> generated = m.readValue(JsonWriter.toByteArray(doc), Map.class);
        assertEquals(docId, generated.get("id"));
        // and from here on down there will be lots of unchecked casting and
        // other fun. This is OK here, because if the casts fail, the should and
        // will fail anyway
        Map<?, ?> inputMap = (Map<?, ?>) m.readValue(Utf8.toBytes(fields), Map.class).get("actualMapStringToArrayOfInt");
        Map<?, ?>  generatedMap = (Map<?, ?> ) ((Map<?, ?>) generated.get("fields")).get("actualMapStringToArrayOfInt");
        assertEquals(inputMap, generatedMap);
    }

    @Test
    public final void oldStringToArrayOfIntMapTest() throws IOException {
        String docId = "id:unittest:testMapStringToArrayOfInt::whee";
        String fields = "{ \"actualMapStringToArrayOfInt\": ["
                + "{ \"key\": \"bamse\", \"value\": [1, 2, 3] }" + "]}";
        Document doc = readDocumentFromJson(docId, fields);
        // we have to do everything by hand to check, as maps are unordered, but
        // are serialized as an ordered structure

        ObjectMapper m = new ObjectMapper();
        Map<?, ?> generated = m.readValue(JsonWriter.toByteArray(doc), Map.class);
        assertEquals(docId, generated.get("id"));
        // and from here on down there will be lots of unchecked casting and
        // other fun. This is OK here, because if the casts fail, the should and
        // will fail anyway
        List<?> inputMap = (List<?>) m.readValue(Utf8.toBytes(fields), Map.class).get("actualMapStringToArrayOfInt");
        Map<?, ?>  generatedMap = (Map<?, ?> ) ((Map<?, ?>) generated.get("fields")).get("actualMapStringToArrayOfInt");
        assertEquals(populateMap(inputMap), generatedMap);
    }

    private Document readDocumentFromJson(String docId, String fields) throws IOException {
        InputStream rawDoc = new ByteArrayInputStream(asFeed(docId, fields));

        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        DocumentParseInfo raw = r.parseDocument().get();
        DocumentType docType = r.readDocumentType(raw.documentId);
        DocumentPut put = new DocumentPut(new Document(docType, raw.documentId));
        new VespaJsonDocumentReader(false).readPut(raw.fieldsBuffer, put);
        return put.getDocument();
    }

    private void assertEqualJson(byte[] expected, byte[] generated) throws IOException {
        ObjectMapper m = new ObjectMapper();
        Map<?, ?> exp = m.readValue(expected, Map.class);
        Map<?, ?> gen = m.readValue(generated, Map.class);
        if (! exp.equals(gen)) {
            System.err.println("expected:  "+Utf8.toString(expected));
            System.err.println("generated: "+Utf8.toString(generated));
        }
        assertEquals(exp, gen);
    }

    private byte[] asFeed(String docId, String fields) {
        return completeDocString("put", docId, fields);
    }

    private byte[] asDocument(String docId, String fields) {
        return completeDocString("id", docId, fields);
    }

    private byte[] completeDocString(String operation, String docId, String fields) {
        return Utf8.toBytes("{\"" + operation + "\": \"" + docId + "\", \"fields\": " + fields + "}");
    }

    @Test
    public void removeTest() {
        final DocumentId documentId = new DocumentId("id:unittest:smoke::whee");
        InputStream rawDoc = new ByteArrayInputStream(
                Utf8.toBytes("["
                + Utf8.toString(JsonWriter.documentRemove(documentId))
                + "]"));
        JsonReader r = new JsonReader(types, rawDoc, parserFactory);
        DocumentOperation actualRemoveAsBaseType = r.next();
        assertSame(DocumentRemove.class, actualRemoveAsBaseType.getClass());
        assertEquals(actualRemoveAsBaseType.getId(), documentId);
    }

    @Test
    public void testWritingWithoutTensorFieldValue() throws IOException {
        roundTripEquality("id:unittest:testtensor::0", "{}");
    }

    @Test
    public void testWritingOfEmptyTensor() throws IOException {
        assertTensorRoundTripEquality("{}","{ \"cells\": [] }");
    }

    @Test
    public void testWritingOfTensorWithCellsOnly() throws IOException {
        assertTensorRoundTripEquality("{ "
                + "  \"cells\": [ "
                + "    { \"address\": { \"x\": \"a\", \"y\": \"b\" }, "
                + "      \"value\": 2.0 }, "
                + "    { \"address\": { \"x\": \"c\", \"y\": \"b\" }, "
                + "      \"value\": 3.0 } "
                + "  ]"
                + "}", "{ "
                + "  \"cells\": [ "
                + "    { \"address\": { \"x\": \"a\", \"y\": \"b\" }, "
                + "      \"value\": 2.0 }, "
                + "    { \"address\": { \"x\": \"c\", \"y\": \"b\" }, "
                + "      \"value\": 3.0 } "
                + "  ]"
                + "}");
    }

    @Test
    public void testWritingOfTensorFieldValueWithoutTensor() throws IOException {
        DocumentType documentTypeWithTensor = types.getDocumentType("testtensor");
        String docId = "id:unittest:testtensor::0";
        Document doc = new Document(documentTypeWithTensor, docId);
        Field tensorField = documentTypeWithTensor.getField("tensorfield");
        doc.setFieldValue(tensorField, new TensorFieldValue(((TensorDataType)tensorField.getDataType()).getTensorType()));
        assertEqualJson(asDocument(docId, "{ \"tensorfield\": {} }"), JsonWriter.toByteArray(doc));
    }

    private void assertTensorRoundTripEquality(String tensorField) throws IOException {
        assertTensorRoundTripEquality(tensorField, tensorField);
    }

    private void assertTensorRoundTripEquality(String inputTensorField, String outputTensorField) throws IOException {
        String inputFields = "{ \"tensorfield\": " + inputTensorField + " }";
        String outputFields = "{ \"tensorfield\": " + outputTensorField + " }";
        String docId = "id:unittest:testtensor::0";
        Document doc = readDocumentFromJson(docId, inputFields);
        assertEqualJson(asDocument(docId, outputFields), JsonWriter.toByteArray(doc));
    }

    @Test
    public void testTensorShortForm() throws IOException {
        DocumentType documentTypeWithTensor = types.getDocumentType("testindexedtensor");
        String docId = "id:unittest:testindexedtensor::0";
        Document doc = new Document(documentTypeWithTensor, docId);
        Field tensorField = documentTypeWithTensor.getField("tensorfield");
        Tensor tensor = Tensor.from("tensor(x[3]):[1,2,3]");
        doc.setFieldValue(tensorField, new TensorFieldValue(tensor));

        assertEqualJson(asDocument(docId, "{ \"tensorfield\": {\"cells\":[{\"address\":{\"x\":\"0\"},\"value\":1.0},{\"address\":{\"x\":\"1\"},\"value\":2.0},{\"address\":{\"x\":\"2\"},\"value\":3.0}]} }"),
                        writeDocument(doc, false));
        assertEqualJson(asDocument(docId, "{ \"tensorfield\": {\"type\":\"tensor(x[3])\", \"values\":[1.0, 2.0, 3.0] } }"),
                        writeDocument(doc, true));
    }

    private byte[] writeDocument(Document doc, boolean tensorShortForm) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonFactory factory = new JsonFactory();
        JsonGenerator generator = factory.createGenerator(out);
        JsonWriter writer = new JsonWriter(generator, tensorShortForm);
        writer.write(doc);
        return out.toByteArray();
    }

    @Test
    public void non_empty_reference_field_is_roundtrip_json_serialized() throws IOException {
        roundTripEquality("id:unittest:testrefs::helloworld",
                "{ \"ref_field\": \"id:unittest:smoke::and_mirrors_too\" }");
    }

    @Test
    public void non_empty_reference_field_results_in_reference_value_with_doc_id_present() throws IOException {
        final Document doc = readDocumentFromJson("id:unittest:testrefs::helloworld",
                "{ \"ref_field\": \"id:unittest:smoke::and_mirrors_too\" }");
        ReferenceFieldValue ref = (ReferenceFieldValue)doc.getFieldValue("ref_field");
        assertTrue(ref.getDocumentId().isPresent());
        assertEquals(new DocumentId("id:unittest:smoke::and_mirrors_too"), ref.getDocumentId().get());
    }

    @Test
    public void empty_reference_field_is_roundtrip_json_serialized() throws IOException {
        roundTripEquality("id:unittest:testrefs::helloworld",
                "{ \"ref_field\": \"\" }");
    }

    @Test
    public void empty_reference_field_results_in_reference_value_without_doc_id_present() throws IOException {
        final Document doc = readDocumentFromJson("id:unittest:testrefs::helloworld", "{ \"ref_field\": \"\" }");
        ReferenceFieldValue ref = (ReferenceFieldValue)doc.getFieldValue("ref_field");
        assertFalse(ref.getDocumentId().isPresent());
    }

}
