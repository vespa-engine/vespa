// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.Field;
import com.yahoo.document.annotation.AbstractTypesTest;
import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.document.annotation.AnnotationTypeRegistry;
import com.yahoo.document.annotation.Span;
import com.yahoo.document.annotation.SpanList;
import com.yahoo.document.annotation.SpanNode;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.vespa.objects.BufferSerializer;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Einar M R Rosenvinge
 */
public class StringTestCase extends AbstractTypesTest {

    @Test
    public void testDeserialize() {
        byte[] buf = new byte[1500];
        GrowableByteBuffer data = GrowableByteBuffer.wrap(buf);
        //short string
        java.lang.String foo = "foo";

        data.put((byte)0);
        data.put((byte)(foo.length() + 1));

        data.put(foo.getBytes());
        data.put((byte)0);

        int positionAfterPut = data.position();

        data.position(0);

        StringFieldValue tmp = new StringFieldValue();
        DocumentDeserializer deser = DocumentDeserializerFactory.create6(null, data);
        tmp.deserialize(deser);
        java.lang.String foo2 = tmp.getString();

        assertTrue(foo.equals(foo2));
        assertEquals(data.position(), positionAfterPut);

        //=====================

        buf = new byte[1500];
        data = GrowableByteBuffer.wrap(buf);

        //long string
        java.lang.String blah = "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah" +
                                "blahblahblahblahblahblahblahblahblahblahblah";

        int length = blah.length() + 1;

        data.put((byte)0);
        data.putInt(length | 0x80000000);

        data.put(blah.getBytes());
        data.put((byte)0);

        positionAfterPut = data.position();

        data.position(0);

        tmp = new StringFieldValue();

        deser = DocumentDeserializerFactory.create6(null, data);
        tmp.deserialize(deser);
        java.lang.String blah2 = tmp.getString();

        assertEquals(data.position(), positionAfterPut);
        assertTrue(blah.equals(blah2));
    }

    @Test
    public void testSerializeDeserialize() {
        java.lang.String test = "Hello hello";
        BufferSerializer data = new BufferSerializer(new GrowableByteBuffer(100, 2.0f));
        StringFieldValue value = new StringFieldValue(test);
        value.serialize(data);

        data.getBuf().position(0);

        StringFieldValue tmp = new StringFieldValue();
        DocumentDeserializer deser = DocumentDeserializerFactory.create6(null, data.getBuf());
        tmp.deserialize(deser);
        java.lang.String test2 = tmp.getString();
        assertEquals(test, test2);
    }

    @Test
    public void testSerializationWithTree() {
        StringFieldValue text = getAnnotatedString();

        serializeAndAssert(text);
    }

    private void serializeAndAssert(StringFieldValue stringFieldValue) {
        Field f = new Field("text", DataType.STRING);

        GrowableByteBuffer buffer = new GrowableByteBuffer(1024);
        DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);
        serializer.write(f, stringFieldValue);
        buffer.flip();

        DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(man, buffer);
        StringFieldValue stringFieldValue2 = new StringFieldValue();
        deserializer.read(f, stringFieldValue2);

        assertEquals(stringFieldValue, stringFieldValue2);
        assertNotSame(stringFieldValue, stringFieldValue2);
    }

    @Test
    public void testNestedSpanTree() {
        AnnotationType type = new AnnotationType("ann", DataType.STRING);

        StringFieldValue outerString = new StringFieldValue("Ballooo");
        SpanTree outerTree = new SpanTree("outer");
        outerString.setSpanTree(outerTree);

        SpanList outerRoot = (SpanList)outerTree.getRoot();
        Span outerSpan = new Span(0, 1);
        outerRoot.add(outerSpan);

        StringFieldValue innerString = new StringFieldValue("innerBalloooo");

        outerTree.annotate(outerSpan, new Annotation(type, innerString));

        SpanTree innerTree = new SpanTree("inner");
        innerString.setSpanTree(innerTree);

        SpanList innerRoot = (SpanList)innerTree.getRoot();
        Span innerSpan = new Span(0, 1);
        innerRoot.add(innerSpan);
        innerTree.annotate(innerSpan, new Annotation(type));

        GrowableByteBuffer buffer = new GrowableByteBuffer(1024);
        DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);

        try {
            serializer.write(null, outerString);
            fail("Should have failed, nested span trees are not supported.");
        } catch (SerializationException se) {
            //OK!
        }
    }

    /**
     * Test for bug 4066566. No assertions, but works if it runs without exceptions.
     */
    @Test
    public void testAnnotatorConsumer() {
        DocumentTypeManager manager = new DocumentTypeManager();
        DocumentTypeManagerConfigurer
                .configure(manager, "file:src/test/java/com/yahoo/document/datatypes/documentmanager.blog.sd");

        DocumentType blogType = manager.getDocumentType("blog");
        Document doc = new Document(blogType, "id:ns:blog::http://blogs.sun.com/praveenm");
        doc.setFieldValue("url", new StringFieldValue("http://blogs.sun.com/praveenm"));
        doc.setFieldValue("title", new StringFieldValue("Beginning JavaFX"));
        doc.setFieldValue("author", new StringFieldValue("Praveen Mohan"));
        doc.setFieldValue("body", new StringFieldValue(
                "JavaFX can expand its wings across different domains such as manufacturing, logistics, retail, etc. Many companies have adopted it - IBM, Oracle, Yahoo, Honeywell. Even the non-IT industries such as GE, WIPRO, Ford etc. So it is a success for Christopher Oliver and Richard Bair. Scott Mcnealy is happy"));

        doc = annotate(doc, manager);
        doc = serializeAndDeserialize(doc, manager);
    }

    private Document serializeAndDeserialize(Document doc, DocumentTypeManager manager) {
        GrowableByteBuffer buffer = new GrowableByteBuffer(1024);
        DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);
        serializer.write(doc);
        buffer.flip();

        DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(manager, buffer);
        return new Document(deserializer);
    }

    public Document annotate(Document document, DocumentTypeManager manager) {
        AnnotationTypeRegistry registry = manager.getAnnotationTypeRegistry();

        AnnotationType company = registry.getType("company");
        AnnotationType industry = registry.getType("industry");
        AnnotationType person = registry.getType("person");
        AnnotationType location = registry.getType("location");

        Map<String, AnnotationType> m = registry.getTypes();

        SpanTree tree = new SpanTree("testannotations");
        SpanList root = (SpanList)tree.getRoot();

        SpanNode companySpan = new Span(0, 5);
        SpanNode industrySpan = new Span(5, 10);
        SpanNode personSpan = new Span(10, 15);
        SpanNode locationSpan = new Span(15, 20);

        root.add(companySpan);
        root.add(industrySpan);
        root.add(personSpan);
        root.add(locationSpan);

        Struct companyValue = (Struct)company.getDataType().createFieldValue();
        companyValue.setFieldValue("name", new StringFieldValue("Sun"));
        companyValue.setFieldValue("ceo", new StringFieldValue("Scott Mcnealy"));
        companyValue.setFieldValue("lat", new DoubleFieldValue(37.7));
        companyValue.setFieldValue("lon", new DoubleFieldValue(-122.44));
        companyValue.setFieldValue("vertical", new StringFieldValue("software"));
        Annotation compAn = new Annotation(company, companyValue);
        tree.annotate(companySpan, compAn);

        Struct personValue = new Struct(person.getDataType());
        personValue.setFieldValue("name", new StringFieldValue("Richard Bair"));
        Annotation personAn = new Annotation(person, personValue);
        tree.annotate(personSpan, personAn);

        Struct locValue = new Struct(location.getDataType());
        locValue.setFieldValue("name", new StringFieldValue("Prinsens Gate"));
        Annotation loc = new Annotation(location, locValue);
        tree.annotate(locationSpan, loc);

        Struct locValue2 = new Struct(location.getDataType());
        locValue2.setFieldValue("name", new StringFieldValue("Kongens Gate"));
        Annotation locAn = new Annotation(location, locValue2);
        tree.annotate(locationSpan, locAn);

        SpanList branch = new SpanList();

        SpanNode span1 = new Span(0, 3);
        SpanNode span2 = new Span(1, 9);
        SpanNode span3 = new Span(12, 10);

        branch.add(span1);
        branch.add(span3);
        branch.add(span2);

        Struct industryValue = new Struct(industry.getDataType());
        industryValue.setFieldValue("vertical", new StringFieldValue("Manufacturing"));
        Annotation ind = new Annotation(industry, industryValue);
        tree.annotate(span1, ind);

        Struct pValue = new Struct(person.getDataType());
        pValue.setFieldValue("name", new StringFieldValue("Praveen Mohan"));
        Annotation pAn = new Annotation(person, pValue);
        tree.annotate(span2, pAn);

        Struct lValue = new Struct(location.getDataType());
        lValue.setFieldValue("name", new StringFieldValue("Embassy Golf Links"));
        Annotation locn = new Annotation(location, lValue);
        tree.annotate(span3, locn);

        Struct cValue = (Struct)company.getDataType().createFieldValue();
        cValue.setFieldValue("name", new StringFieldValue("Yahoo"));
        cValue.setFieldValue("ceo", new StringFieldValue("Carol Bartz"));
        cValue.setFieldValue("lat", new DoubleFieldValue(127.7));
        cValue.setFieldValue("lon", new DoubleFieldValue(-42.44));
        cValue.setFieldValue("vertical", new StringFieldValue("search"));
        Annotation cAn = new Annotation(company, cValue);
        tree.annotate(branch, cAn);

        Struct pVal = new Struct(person.getDataType());
        pVal.setFieldValue("name", new StringFieldValue("Kim Omar"));
        Annotation an = new Annotation(person, pVal);
        tree.annotate(root, an);
        root.add(branch);

        StringFieldValue body = (StringFieldValue)document.getFieldValue(document.getDataType().getField("body"));

        root.remove(branch);
        tree.cleanup();

        assertEquals(5, tree.numAnnotations());
        body.setSpanTree(tree);

        document.setFieldValue(document.getField("body"), body);

        return document;
    }

}
