// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.*;

public class Bug6394548TestCase {
    @Test
    @SuppressWarnings("deprecation")
    public void testSerializeAndDeserializeMultipleAdjacentStructAnnotations() {
        DocumentTypeManager manager = new DocumentTypeManager();
        var sub = DocumentTypeManagerConfigurer.configure
            (manager, "file:src/test/java/com/yahoo/document/annotation/documentmanager.6394548.cfg");
        sub.close();

        AnnotationTypeRegistry registry = manager.getAnnotationTypeRegistry();
        AnnotationType featureSetType = registry.getType("morty.RICK_FEATURESET");
        assertNotNull(featureSetType);

        Document doc = new Document(manager.getDocumentType("article"), "id:ns:article::test");
        StringFieldValue sfv = new StringFieldValue("badger waltz");

        SpanList root = new SpanList();
        SpanNode node = new Span(0, sfv.getString().length());
        root.add(node);

        SpanTree tree = new SpanTree("rick_features", root);
        for (int i = 0; i < 2; ++i) {
            tree.annotate(createBigFeatureSetAnnotation(featureSetType));
        }

        sfv.setSpanTree(tree);
        doc.setFieldValue("title", sfv);
        System.out.println(doc.toXml());
        String annotationsBefore = dumpAllAnnotations(tree);

        GrowableByteBuffer buffer = new GrowableByteBuffer();
        DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);
        serializer.write(doc);

        buffer.flip();
        DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(manager, buffer);
        Document doc2 = new Document(deserializer);

        System.out.println(doc2.toXml());

        StringFieldValue readString = (StringFieldValue)doc2.getFieldValue("title");
        SpanTree readTree = readString.getSpanTree("rick_features");
        assertNotNull(readTree);
        String annotationsAfter = dumpAllAnnotations(readTree);

        System.out.println("before:\n" + annotationsBefore);
        System.out.println("after:\n" + annotationsAfter);

        assertEquals(annotationsBefore, annotationsAfter);
    }

    @SuppressWarnings("deprecation")
    private String dumpAllAnnotations(SpanTree tree) {
        ArrayList<String> tmp = new ArrayList<>();
        for (Annotation anno : tree) {
            Struct s = (Struct)anno.getFieldValue();
            tmp.add(s.toXml());
        }
        Collections.sort(tmp);
        StringBuilder annotations = new StringBuilder();
        for (String s : tmp) {
            annotations.append(s);
        }
        return annotations.toString();
    }

    private Annotation createBigFeatureSetAnnotation(AnnotationType featuresetAnno) {
        StructDataType featuresetType = (StructDataType)featuresetAnno.getDataType();
        Struct featureset = featuresetType.createFieldValue();
        System.out.println("featureset type: " + featureset.getDataType().toString());

        MapFieldValue<StringFieldValue, IntegerFieldValue> discreteValued
                = (MapFieldValue<StringFieldValue, IntegerFieldValue>)featuresetType.getField("discretevaluedfeatures").getDataType().createFieldValue();
        discreteValued.put(new StringFieldValue("foo"), new IntegerFieldValue(1234));
        discreteValued.put(new StringFieldValue("bar"), new IntegerFieldValue(567890123));
        featureset.setFieldValue("discretevaluedfeatures", discreteValued);

        MapFieldValue<StringFieldValue, DoubleFieldValue> realValued
                = (MapFieldValue<StringFieldValue, DoubleFieldValue>)featuresetType.getField("realvaluedfeatures").getDataType().createFieldValue();
        realValued.put(new StringFieldValue("foo"), new DoubleFieldValue(0.75));
        realValued.put(new StringFieldValue("bar"), new DoubleFieldValue(1.5));
        featureset.setFieldValue("realvaluedfeatures", realValued);

        Array<StringFieldValue> nested = (Array<StringFieldValue>)featureset.getField("foo10").getDataType().createFieldValue();
        nested.add(new StringFieldValue("baz"));
        nested.add(new StringFieldValue("blargh"));
        featureset.setFieldValue("foo10", nested);

        featureset.setFieldValue("foo1", new StringFieldValue("asdf"));
        featureset.setFieldValue("foo4", new StringFieldValue("qwerty"));
        featureset.setFieldValue("foo2", new IntegerFieldValue(555));
        featureset.setFieldValue("foo2", new IntegerFieldValue(8));
        featureset.setFieldValue("foo7", new IntegerFieldValue(1337));
        featureset.setFieldValue("foo8", new IntegerFieldValue(967867));
        featureset.setFieldValue("foo9", new DoubleFieldValue(123.45));

        Array<StringFieldValue> attributes = (Array<StringFieldValue>)featureset.getField("foo6").getDataType().createFieldValue();
        attributes.add(new StringFieldValue("adam"));
        attributes.add(new StringFieldValue("jamie"));
        attributes.add(new StringFieldValue("grant"));
        attributes.add(new StringFieldValue("tory"));
        attributes.add(new StringFieldValue("kari"));
        featureset.setFieldValue("variantattribute", attributes);

        Annotation anno = new Annotation(featuresetAnno);
        anno.setFieldValue(featureset);

        return anno;
    }
}
