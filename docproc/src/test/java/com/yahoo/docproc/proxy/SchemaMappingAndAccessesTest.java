// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.proxy;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.yahoo.collections.Pair;
import com.yahoo.config.docproc.SchemamappingConfig;
import com.yahoo.docproc.Accesses;
import com.yahoo.docproc.Accesses.Field;
import com.yahoo.docproc.Call;
import com.yahoo.docproc.DocumentProcessingAbstractTestCase.TestDocumentProcessor1;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.StructDataType;
import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.update.FieldUpdate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("unchecked")
public class SchemaMappingAndAccessesTest {

    private Document getDoc() {
        DocumentType type = new DocumentType("album");
        AnnotationType personType = new AnnotationType("person");
        Annotation person = new Annotation(personType);
        type.addField("title", DataType.STRING);
        type.addField("artist", DataType.STRING);
        type.addField("guitarist", DataType.STRING);
        type.addField("year", DataType.INT);
        type.addField("labels", DataType.getArray(DataType.STRING));
        Document doc = new Document(type, new DocumentId("doc:map:test:1"));
        doc.setFieldValue("title", new StringFieldValue("Black Rock"));
        StringFieldValue joe = new StringFieldValue("Joe Bonamassa");
        joe.setSpanTree(new SpanTree("mytree").annotate(person));
        doc.setFieldValue("artist", joe);
        doc.setFieldValue("year", new IntegerFieldValue(2010));
        Array<StringFieldValue> labels = new Array<>(type.getField("labels").getDataType());
        labels.add(new StringFieldValue("audun"));
        labels.add(new StringFieldValue("tylden"));
        doc.setFieldValue("labels", labels);

        StructDataType personStructType = new StructDataType("artist");
        personStructType.addField(new com.yahoo.document.Field("firstname", DataType.STRING));
        personStructType.addField(new com.yahoo.document.Field("lastname", DataType.STRING));
        type.addField("listeners", DataType.getArray(personStructType));

        Array<Struct> listeners = new Array<>(type.getField("listeners").getDataType());

        Struct listenerOne = new Struct(personStructType);
        listenerOne.setFieldValue("firstname", new StringFieldValue("per"));
        listenerOne.setFieldValue("lastname", new StringFieldValue("olsen"));
        Struct listenerTwo = new Struct(personStructType);
        listenerTwo.setFieldValue("firstname", new StringFieldValue("anders"));
        listenerTwo.setFieldValue("lastname", new StringFieldValue("and"));

        listeners.add(listenerOne);
        listeners.add(listenerTwo);

        doc.setFieldValue("listeners", listeners);

        return doc;
    }

    @Test
    public void testMappingArrays() {
        Document doc = getDoc();
        DocumentProcessor proc = new TestMappingArrayProcessor();

        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("label", "labels[0]");
        ProxyDocument mapped = new ProxyDocument(proc, doc, fieldMap);

        Processing p = Processing.of(new DocumentPut(mapped));
        proc.process(p);

        assertEquals(2, ((Array<StringFieldValue>) doc.getFieldValue("labels")).size());
        assertEquals(new StringFieldValue("EMI"), ((Array<StringFieldValue>) doc.getFieldValue("labels")).get(0));
        assertEquals(new StringFieldValue("tylden"), ((Array<StringFieldValue>) doc.getFieldValue("labels")).get(1));


        fieldMap.clear();
        fieldMap.put("label", "labels[2]");
        mapped = new ProxyDocument(proc, doc, fieldMap);

        p = Processing.of(new DocumentPut(mapped));
        try {
            proc.process(p);
            fail("Should not have worked");
        } catch (IllegalArgumentException iae) {
            //ok!
        }
        assertEquals(2, ((Array<StringFieldValue>) doc.getFieldValue("labels")).size());
        assertEquals(new StringFieldValue("EMI"), ((Array<StringFieldValue>) doc.getFieldValue("labels")).get(0));
        assertEquals(new StringFieldValue("tylden"), ((Array<StringFieldValue>) doc.getFieldValue("labels")).get(1));
    }

    @Test
    public void testMappingStructsInArrays() {
        Document doc = getDoc();
        DocumentProcessor proc = new TestMappingStructInArrayProcessor();

        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("name", "listeners[0].firstname");
        ProxyDocument mapped = new ProxyDocument(proc, doc, fieldMap);

        Processing p = Processing.of(new DocumentPut(mapped));
        proc.process(p);

        assertEquals(2, ((Array<Struct>) doc.getFieldValue("listeners")).size());
        assertEquals("peter", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(0).getFieldValue("firstname")).getString()));
        assertEquals("olsen", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(0).getFieldValue("lastname")).getString()));
        assertEquals("anders", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(1).getFieldValue("firstname")).getString()));
        assertEquals("and", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(1).getFieldValue("lastname")).getString()));


        fieldMap.clear();
        fieldMap.put("name", "listeners[2].firstname");
        mapped = new ProxyDocument(proc, doc, fieldMap);

        p = Processing.of(new DocumentPut(mapped));
        try {
            proc.process(p);
            fail("Should not have worked");
        } catch (IllegalArgumentException iae) {
            //ok!
        }
        assertEquals(2, ((Array<Struct>) doc.getFieldValue("listeners")).size());
        assertEquals("peter", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(0).getFieldValue("firstname")).getString()));
        assertEquals("olsen", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(0).getFieldValue("lastname")).getString()));
        assertEquals("anders", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(1).getFieldValue("firstname")).getString()));
        assertEquals("and", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(1).getFieldValue("lastname")).getString()));


        //test remove:
        proc = new TestRemovingMappingStructInArrayProcessor();

        fieldMap.clear();
        fieldMap.put("name", "listeners[1].lastname");
        mapped = new ProxyDocument(proc, doc, fieldMap);

        p = Processing.of(new DocumentPut(mapped));
        proc.process(p);

        assertEquals(2, ((Array<Struct>) doc.getFieldValue("listeners")).size());
        assertEquals("peter", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(0).getFieldValue("firstname")).getString()));
        assertEquals("olsen", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(0).getFieldValue("lastname")).getString()));
        assertEquals("anders", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(1).getFieldValue("firstname")).getString()));
        assertNull(((Array<Struct>) doc.getFieldValue("listeners")).get(1).getFieldValue("lastname"));


        fieldMap.clear();
        fieldMap.put("name", "listeners[2].lastname");
        mapped = new ProxyDocument(proc, doc, fieldMap);

        p = Processing.of(new DocumentPut(mapped));
        try {
            proc.process(p);
            fail("Should not have worked");
        } catch (IllegalArgumentException iae) {
            //ok!
        }
        assertEquals(2, ((Array<Struct>) doc.getFieldValue("listeners")).size());
        assertEquals("peter", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(0).getFieldValue("firstname")).getString()));
        assertEquals("olsen", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(0).getFieldValue("lastname")).getString()));
        assertEquals("anders", (((StringFieldValue)((Array<Struct>) doc.getFieldValue("listeners")).get(1).getFieldValue("firstname")).getString()));
        assertNull(((Array<Struct>) doc.getFieldValue("listeners")).get(1).getFieldValue("lastname"));

    }

    @Test
    public void testMappingSpanTrees() {
        Document doc = getDoc();
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("t", "title");
        fieldMap.put("a", "artist");
        fieldMap.put("g", "guitarist");
        ProxyDocument mapped = new ProxyDocument(new TestDocumentProcessor1(), doc, fieldMap);
        Iterator<SpanTree> itSpanTreesDoc = ((StringFieldValue) doc.getFieldValue("artist")).getSpanTrees().iterator();
        Iterator<Annotation> itAnnotDoc = itSpanTreesDoc.next().iterator();
        Iterator<SpanTree> itSpanTreesMapped = ((StringFieldValue) mapped.getFieldValue("artist")).getSpanTrees().iterator();
        Iterator<Annotation> itAnnotMapped = itSpanTreesMapped.next().iterator();

        assertEquals(itAnnotDoc.next().getType().getName(), "person");
        assertFalse(itAnnotDoc.hasNext());
        assertEquals(itAnnotMapped.next().getType().getName(), "person");
        assertFalse(itAnnotMapped.hasNext());

        AnnotationType guitaristType = new AnnotationType("guitarist");
        Annotation guitarist = new Annotation(guitaristType);
        StringFieldValue bona = new StringFieldValue("Bonamassa");
        bona.setSpanTree(new SpanTree("mytree").annotate(guitarist));
        StringFieldValue clapton = new StringFieldValue("Clapton");
        mapped.setFieldValue("a", bona);
        mapped.setFieldValue("g", clapton);

        itSpanTreesDoc = ((StringFieldValue) doc.getFieldValue("artist")).getSpanTrees().iterator();
        itAnnotDoc = itSpanTreesDoc.next().iterator();
        itSpanTreesMapped = ((StringFieldValue) mapped.getFieldValue("artist")).getSpanTrees().iterator();
        itAnnotMapped = itSpanTreesMapped.next().iterator();

        assertEquals(itAnnotDoc.next().getType().getName(), "guitarist");
        assertFalse(itAnnotDoc.hasNext());
        assertEquals(itAnnotMapped.next().getType().getName(), "guitarist");
        assertFalse(itAnnotMapped.hasNext());

        assertSame(((StringFieldValue) doc.getFieldValue("artist")).getSpanTrees().iterator().next(), ((StringFieldValue) mapped.getFieldValue("a")).getSpanTrees().iterator().next());
        //assertSame(clapton, mapped.getFieldValue("g"));
        //assertSame(bona, mapped.getFieldValue("a"));
    }

    @Test
    public void testMappedDoc() {
        Document doc = getDoc();
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("t", "title");
        fieldMap.put("a", "artist");
        ProxyDocument mapped = new ProxyDocument(new TestDocumentProcessor1(), doc, fieldMap);
        //Document mapped=doc;
        //mapped.setFieldMap(fieldMap);
        assertEquals(new StringFieldValue("Black Rock"), mapped.getFieldValue("t"));
        //assertEquals(new StringFieldValue("Black Rock"), proxy.getFieldValue(new com.yahoo.document.Field("t")));
        assertEquals(new StringFieldValue("Joe Bonamassa").getWrappedValue(), mapped.getFieldValue("a").getWrappedValue());
        mapped.setFieldValue("t", new StringFieldValue("The Ballad Of John Henry"));
        StringFieldValue bona = new StringFieldValue("Bonamassa");
        mapped.setFieldValue("a", bona);
        //mapped.setFieldValue("a", new StringFieldValue("Bonamassa"));
        assertEquals(new StringFieldValue("The Ballad Of John Henry"), doc.getFieldValue("title"));
        assertEquals(new StringFieldValue("The Ballad Of John Henry"), mapped.getFieldValue("t"));
        assertEquals(new StringFieldValue("Bonamassa"), doc.getFieldValue("artist"));
        assertEquals(new StringFieldValue("Bonamassa"), mapped.getFieldValue("a"));
        mapped.setFieldValue("a", mapped.getFieldValue("a") + "Hughes");
        assertEquals(new StringFieldValue("BonamassaHughes"), mapped.getFieldValue("a"));
        // Verify consistency when using string values to manipluate annotation span trees
        StringFieldValue unmapped1 = (StringFieldValue) doc.getFieldValue("artist");
        StringFieldValue unmapped2 = (StringFieldValue) doc.getFieldValue("artist");
        assertTrue(unmapped1==unmapped2);
        unmapped1.setSpanTree(new SpanTree("test"));
        assertEquals(unmapped2.getSpanTree("test").getName(), "test");

        StringFieldValue mapped1 = (StringFieldValue) mapped.getFieldValue("a");
        mapped1.setSpanTree(new SpanTree("test2"));
        StringFieldValue mapped2 = (StringFieldValue) mapped.getFieldValue("a");
        assertTrue(mapped1==mapped2);
        assertEquals(mapped2.getSpanTree("test2").getName(), "test2");

        mapped.removeFieldValue("a");
        assertEquals(mapped.getFieldValue("a"), null);
        mapped.removeFieldValue(mapped.getField("t"));
        assertEquals(mapped.getFieldValue("t"), null);
        mapped.setFieldValue("a", new StringFieldValue("Bonamassa"));
        assertEquals(new StringFieldValue("Bonamassa"), doc.getFieldValue("artist"));
        mapped.removeFieldValue("a");
        assertEquals(mapped.getFieldValue("a"), null);
    }

    @Test
    public void testMappedDocAPI() {
        Document doc = getDoc();
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("t", "title");
        fieldMap.put("a", "artist");
        ProxyDocument mapped = new ProxyDocument(new TestDocumentProcessor1(), doc, fieldMap);
        assertEquals(mapped.getFieldValue("title"), doc.getFieldValue("title"));
        assertEquals(mapped.getFieldValue(new com.yahoo.document.Field("title")), doc.getFieldValue((new com.yahoo.document.Field("title"))));
        mapped.setFieldValue("title", "foo");
        assertEquals(doc.getFieldValue("title").getWrappedValue(), "foo");
        assertEquals(mapped.getWrappedDocumentOperation().getId().toString(), "doc:map:test:1");
        assertEquals(doc, mapped);
        assertEquals(doc.toString(), mapped.toString());
        assertEquals(doc.hashCode(), mapped.hashCode());
        assertEquals(doc.clone(), mapped.clone());
        assertEquals(doc.iterator().hasNext(), mapped.iterator().hasNext());
        assertEquals(doc.getId(), mapped.getId());
        assertEquals(doc.getDataType(), mapped.getDataType());
        mapped.setLastModified(56l);
        assertEquals(doc.getLastModified(), (Long)56l);
        assertEquals(mapped.getLastModified(), (Long)56l);
        mapped.setId(new DocumentId("doc:map:test:2"));
        assertEquals(mapped.getId().toString(), "doc:map:test:2");
        assertEquals(doc.getId().toString(), "doc:map:test:2");
        assertEquals(doc.getHeader(), mapped.getHeader());
        assertEquals(doc.getBody(), mapped.getBody());
        assertEquals(doc.getSerializedSize(), mapped.getSerializedSize());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        mapped.serialize(bos);
        doc.serialize(bos2);
        assertEquals(bos.toString(), bos2.toString());
        assertEquals(mapped.toXml(), doc.toXml());
        assertEquals(mapped.getFieldCount(), doc.getFieldCount());
        assertTrue(mapped.getDocument()==doc);
        
        mapped.clear();
        assertNull(mapped.getFieldValue("title"));
        assertNull(doc.getFieldValue("title"));
        mapped.setDataType(new DocumentType("newType"));
        assertEquals(doc.getDataType().getName(), "newType");
    }

    @Test
    public void testMappedDocUpdateAPI() {
        Document doc = getDoc();
        DocumentType type = doc.getDataType();
        DocumentUpdate dud = new DocumentUpdate(type, new DocumentId("doc:map:test:1"));
        FieldUpdate assignSingle = FieldUpdate.createAssign(type.getField("title"), new StringFieldValue("something"));
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("t", "title");
        fieldMap.put("a", "artist");
        ProxyDocumentUpdate pup = new ProxyDocumentUpdate(dud, fieldMap);
        pup.addFieldUpdate(assignSingle);
        assertEquals(pup.getFieldUpdates(), dud.getFieldUpdates());
        assertEquals(pup.getDocumentType(), dud.getDocumentType());
        assertEquals(pup.getFieldUpdate(new com.yahoo.document.Field("title")).size(), 1);
        assertEquals(pup.getFieldUpdate(0), dud.getFieldUpdate(0));
        assertEquals(pup.getFieldUpdate("title"), dud.getFieldUpdate("title"));
        assertEquals(pup.getId(), dud.getId());
        assertEquals(pup.getType(), dud.getType());
        assertEquals(pup.applyTo(doc), dud);
        assertEquals(doc.getFieldValue("title").getWrappedValue(), "something");
        assertEquals(pup, dud);
        assertEquals(pup.hashCode(), dud.hashCode());
        assertEquals(pup.toString(), dud.toString());
        assertEquals(pup.size(), dud.size());
        assertEquals(pup.getWrappedDocumentOperation().getId().toString(), "doc:map:test:1");
    }

    @Test
    public void testMappedDocStruct() {
        StructDataType materialsStructType = new StructDataType("materialstype");
        materialsStructType.addField(new com.yahoo.document.Field("ceiling", DataType.STRING));
        materialsStructType.addField(new com.yahoo.document.Field("walls", DataType.STRING));

        DocumentType docType = new DocumentType("album");
        docType.addField("title", DataType.STRING);
        docType.addField("artist", DataType.STRING);
        StructDataType storeStructType = new StructDataType("storetype");
        storeStructType.addField(new com.yahoo.document.Field("name", DataType.STRING));
        storeStructType.addField(new com.yahoo.document.Field("city", DataType.STRING));
        storeStructType.addField(new com.yahoo.document.Field("materials", materialsStructType));
        docType.addField("store", storeStructType);

        Document doc = new Document(docType, new DocumentId("doc:map:test:1"));
        doc.setFieldValue("title", new StringFieldValue("Black Rock"));
        doc.setFieldValue("artist", new StringFieldValue("Joe Bonamassa"));
        Struct material = new Struct(materialsStructType);
        material.setFieldValue("ceiling", new StringFieldValue("wood"));
        material.setFieldValue("walls", new StringFieldValue("brick"));
        Struct store = new Struct(storeStructType);
        store.setFieldValue("name", new StringFieldValue("Platekompaniet"));
        store.setFieldValue("city", new StringFieldValue("Trondheim"));
        store.setFieldValue(storeStructType.getField("materials"), material);
        doc.setFieldValue(docType.getField("store"), store);

        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("t", "title");
        fieldMap.put("c", "store.city");
        fieldMap.put("w", "store.materials.walls");
        ProxyDocument mapped = new ProxyDocument(new TestDocumentProcessor1(), doc, fieldMap);
        assertEquals(new StringFieldValue("Trondheim"), mapped.getFieldValue("c"));
        assertEquals(new StringFieldValue("Black Rock"), mapped.getFieldValue("t"));
        assertEquals(new StringFieldValue("brick"), mapped.getFieldValue("w"));
        assertEquals(new StringFieldValue("brick"), material.getFieldValue("walls"));
        mapped.setFieldValue("c", new StringFieldValue("Steinkjer"));
        mapped.setFieldValue("w", new StringFieldValue("plaster"));
        assertEquals(new StringFieldValue("plaster"), mapped.getFieldValue("w"));
        assertEquals(new StringFieldValue("plaster"), material.getFieldValue("walls"));
        assertEquals(new StringFieldValue("Steinkjer"), store.getFieldValue("city"));
        assertEquals(new StringFieldValue("Steinkjer"), mapped.getFieldValue("c"));
        assertEquals(new StringFieldValue("Steinkjer"), mapped.getFieldValue("c"));
        mapped.setFieldValue("c", new StringFieldValue("Levanger"));
        assertEquals(new StringFieldValue("Levanger"), store.getFieldValue("city"));
        assertEquals(new StringFieldValue("Levanger"), mapped.getFieldValue("c"));
        mapped.setFieldValue("c", mapped.getFieldValue("c") + "Kommune");
        assertEquals(new StringFieldValue("LevangerKommune"), mapped.getFieldValue("c"));
        //mapped.set(mapped.getField("c"), mapped.get("c")+"Styre");
        //assertEquals(new StringFieldValue("LevangerKommuneStyre"), mapped.getFieldValue("c"));
    }

    @Test
    public void testSchemaMap() {
        SchemaMap map = new SchemaMap();
        map.addMapping("mychain", "com.yahoo.MyDocProc", "mydoctype", "inDoc1", "inProc1");
        map.addMapping("mychain", "com.yahoo.MyDocProc", "mydoctype", "inDoc2", "inProc2");
        Map<Pair<String, String>, String> cMap = map.chainMap("mychain", "com.yahoo.MyDocProc");
        assertEquals("inDoc1", cMap.get(new Pair<>("mydoctype", "inProc1")));
        assertEquals("inDoc2", cMap.get(new Pair<>("mydoctype", "inProc2")));
        assertNull(cMap.get(new Pair<>("invalidtype", "inProc2")));
        Map<Pair<String, String>, String> noMap = map.chainMap("invalidchain", "com.yahoo.MyDocProc");
        Map<Pair<String, String>, String> noMap2 = map.chainMap("mychain", "com.yahoo.MyInvalidDocProc");
        assertTrue(noMap.isEmpty());
        assertTrue(noMap2.isEmpty());

        DocumentProcessor proc = new TestDocumentProcessor1();
        proc.setFieldMap(cMap);
        Map<String, String> dMap = proc.getDocMap("mydoctype");
        assertEquals("inDoc1", dMap.get("inProc1"));
        assertEquals("inDoc2", dMap.get("inProc2"));
    }

    @Test
    public void testSchemaMapKey() {
        SchemaMap map = new SchemaMap(null);
        SchemaMap.SchemaMapKey key1 = map.new SchemaMapKey("chain", "docproc", "doctype", "from");
        SchemaMap.SchemaMapKey key1_1 = map.new SchemaMapKey("chain", "docproc", "doctype", "from");
        SchemaMap.SchemaMapKey key2 = map.new SchemaMapKey("chain", "docproc", "doctype2", "from");
        assertTrue(key1.equals(key1_1));
        assertFalse(key1.equals(key2));
    }

    @Test
    public void testSchemaMapConfig() {
        SchemaMap map = new SchemaMap(null);
        SchemamappingConfig.Builder scb = new SchemamappingConfig.Builder();
        scb.fieldmapping(new SchemamappingConfig.Fieldmapping.Builder().chain("mychain").docproc("mydocproc").doctype("mydoctype").
                indocument("myindoc").inprocessor("myinprocessor"));
        map.configure(new SchemamappingConfig(scb));
        assertEquals(map.chainMap("mychain", "mydocproc").get(new Pair<>("mydoctype", "myinprocessor")), "myindoc");
    }

    @Test
    public void testSchemaMapNoDocType() {
        SchemaMap map = new SchemaMap(null);
        map.addMapping("mychain", "com.yahoo.MyDocProc", null, "inDoc1", "inProc1");
        map.addMapping("mychain", "com.yahoo.MyDocProc", null, "inDoc2", "inProc2");
        Map<Pair<String, String>, String> cMap = map.chainMap("mychain", "com.yahoo.MyDocProc");
        DocumentProcessor proc = new TestDocumentProcessor1();
        proc.setFieldMap(cMap);
        Map<String, String> dMap = proc.getDocMap("mydoctype");
        assertEquals("inDoc1", dMap.get("inProc1"));
        assertEquals("inDoc2", dMap.get("inProc2"));
    }

    @Test
    public void testProxyAndSecure() {
        DocumentProcessor procOK = new TestDPSecure();
        Map<Pair<String, String>, String> fieldMap = new HashMap<>();
        fieldMap.put(new Pair<>("album", "titleMapped"), "title");
        procOK.setFieldMap(fieldMap);
        DocumentPut put = new DocumentPut(getDoc());
        Document proxyDoc = new Call(procOK).configDoc(procOK, put).getDocument();
        procOK.process(Processing.of(new DocumentPut(proxyDoc)));
        assertEquals(proxyDoc.getFieldValue("title").toString(), "MyTitle MyTitle");
    }

    @Test
    public void testProxyAndSecureSecureFailing() {
        DocumentProcessor procInsecure = new TestDPInsecure();
        Map<Pair<String, String>, String> fieldMap = new HashMap<>();
        fieldMap.put(new Pair<>("album", "titleMapped"), "title");
        procInsecure.setFieldMap(fieldMap);
        DocumentPut put = new DocumentPut(getDoc());
        Document doc = new Call(procInsecure).configDoc(procInsecure, put).getDocument();
        try {
            procInsecure.process(Processing.of(new DocumentPut(doc)));
            fail("Insecure docproc went through");
        } catch (Exception e) {
            assertTrue(e.getMessage().matches(".*allowed.*"));
        }
        //assertEquals(doc.get("title"), "MyTitle");
    }

    /**
     * To make it less likely to break schema mapping, we enforce that ProxyDocument does wrap every public
     * non-static, non-final method on Document and StructuredFieldValue
     */
    @Test
    public void testVerifyProxyDocumentOverridesEverything() {
        List<Method> allPublicFromProxyDocument = new ArrayList<>();
        for (Method m : ProxyDocument.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                allPublicFromProxyDocument.add(m);
            }
        }
        List<Method> allPublicFromDoc = new ArrayList<>();
        for (Method m : Document.class.getDeclaredMethods()) {
            if (mustBeOverriddenInProxyDocument(m)) {
                allPublicFromDoc.add(m);
            }
        }
        for (Method m : StructuredFieldValue.class.getDeclaredMethods()) {
            if (mustBeOverriddenInProxyDocument(m)) {
                allPublicFromDoc.add(m);
            }
        }

        for (Method m : allPublicFromDoc) {
            boolean thisOneOk=false;
            for (Method pdM : allPublicFromProxyDocument) {
                if (sameNameAndParams(m, pdM)) thisOneOk=true;
            }
            if (!thisOneOk) {
                throw new RuntimeException("ProxyDocument must override all public methods from Document. " +
                        "Missing: '"+m+"'. If the method doesn't need field mapping or @Accesses check, just " +
                "override it and delegate the call to 'doc'.");

            }
        }
    }

    private boolean mustBeOverriddenInProxyDocument(Method m) {
        if (!Modifier.isPublic(m.getModifiers())) return false;
        if (Modifier.isStatic(m.getModifiers())) return false;
        if (Modifier.isFinal(m.getModifiers())) return false;
        return true;
    }

    private boolean sameNameAndParams(Method m1, Method m2) {
        if (!m1.getName().equals(m2.getName())) return false;
        if (m1.getParameterTypes().length!=m2.getParameterTypes().length) return false;
        for (int i = 0; i<m1.getParameterTypes().length; i++) {
            if (!m1.getParameterTypes()[i].equals(m2.getParameterTypes()[i])) return false;
        }
        return true;
    }

    @Accesses(value = { @Field(dataType = "String", description = "", name = "titleMapped") })
    public static class TestDPSecure extends DocumentProcessor {

        public Progress process(Processing processing) {
            Document document = ((DocumentPut)processing.getDocumentOperations().get(0)).getDocument();
            document.setFieldValue("titleMapped", new StringFieldValue("MyTitle"));
            document.setFieldValue("titleMapped", new StringFieldValue(document.getFieldValue("titleMapped").toString() + " MyTitle"));
            return Progress.DONE;
        }
    }

    @Accesses(value = { @Field(dataType = "String", description = "", name = "titleMappedFoo") })
    public static class TestDPInsecure extends DocumentProcessor {

        public Progress process(Processing processing) {
            Document document = ((DocumentPut)processing.getDocumentOperations().get(0)).getDocument();
            document.setFieldValue("titleMapped", new StringFieldValue("MyTitle"));
            document.setFieldValue("titleMapped", new StringFieldValue(document.getFieldValue("titleMapped").toString() + " MyTitle"));
            return Progress.DONE;
        }
    }

    public static class TestMappingArrayProcessor extends DocumentProcessor {
        public Progress process(Processing processing) {
            Document document = ((DocumentPut)processing.getDocumentOperations().get(0)).getDocument();
            document.setFieldValue("label", new StringFieldValue("EMI"));
            return Progress.DONE;
        }
    }

    public static class TestMappingStructInArrayProcessor extends DocumentProcessor {
        public Progress process(Processing processing) {
            Document document = ((DocumentPut)processing.getDocumentOperations().get(0)).getDocument();;
            document.setFieldValue("name", new StringFieldValue("peter"));
            return Progress.DONE;
        }
    }

    public static class TestRemovingMappingStructInArrayProcessor extends DocumentProcessor {
        public Progress process(Processing processing) {
            Document document = ((DocumentPut)processing.getDocumentOperations().get(0)).getDocument();;
            document.removeFieldValue("name");
            return Progress.DONE;
        }
    }

}
