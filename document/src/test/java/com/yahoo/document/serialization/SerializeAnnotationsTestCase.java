// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.annotation.*;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * Tests that serialization of annotations from Java generates the
 * output expected by the C++ deserialization system.
 *
 * If the format changes and this test starts failing, you should
 * generate new golden files to check against. This will cause the C++
 * test to fail, so you will need to update the
 * AnnotationDeserialization component to handle the format changes.
 */
@SuppressWarnings("deprecation")
public class SerializeAnnotationsTestCase {

    private static final String PATH = "src/tests/serialization/";
    DocumentTypeManager docMan = new DocumentTypeManager();

    @Before
    public void setUp() {
        DocumentTypeManagerConfigurer.configure(docMan,
                                                "file:src/tests/serialization/" +
                                                "annotation.serialize.test.cfg");
    }

    @Test
    public void testSerializeSimpleTree() throws IOException {
        SpanList root = new SpanList();
        root.add(new Span(0, 19))
            .add(new Span(19, 5))
            .add(new Span(24, 21))
            .add(new Span(45, 23))
            .add(new Span(68, 14));
        SpanTree tree = new SpanTree("html", root);
        StringFieldValue value = new StringFieldValue("lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj " +
                                                      "lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj l jlkj lkj lkj " +
                                                      "lkjoijoij oij oij oij oij oij oijoijoij oij oij oij oij oij " +
                                                      "oijoijoijoijoijoijoijoijoijoijoijoijoij oij oij oij oij " +
                                                      "oijaosdifjoai fdoais jdoasi jai os oafoai ai dfojsfoa dfoi dsf" +
                                                      "aosifjasofija sodfij oasdifj aosdiosifjsi ooai oais osi");
        value.setSpanTree(tree);

        /*
        Important note! The iteration order of annotations in SpanTree.iterator() is non-deterministic, meaning
        that the order which annotations are serialized will differ between test runs. Thus, we cannot assert
        that a serialized buffer is equal to a buffer written earlier. We can, however, assert that the size stays
        the same, and the deserialized values from the buffers should be equal.
         */

        //important! call readFile() before writeFile()!
        ByteBuffer serializedFromFile = readFile("test_data_serialized_simple");
        ByteBuffer serialized = writeFile(value, "test_data_serialized_simple");
        assertEquals(serialized.limit(), serializedFromFile.limit());

        StringFieldValue valueFromFile = new StringFieldValue();
        DocumentDeserializer deserializer = DocumentDeserializerFactory.create42(docMan, new GrowableByteBuffer(serializedFromFile));
        deserializer.read(null, valueFromFile);
        assertEquals(value, valueFromFile);
    }

    @Test
    public void testSerializeAdvancedTree() throws IOException {
        SpanList root = new SpanList();
        SpanTree tree = new SpanTree("html", root);

        DataType positionType = docMan.getDataType("myposition");
        StructDataType cityDataType =
            (StructDataType) docMan.getDataType("annotation.city");

        AnnotationTypeRegistry registry = docMan.getAnnotationTypeRegistry();
        AnnotationType textType = registry.getType("text");
        AnnotationType beginTag = registry.getType("begintag");
        AnnotationType endTag = registry.getType("endtag");
        AnnotationType bodyType = registry.getType("body");
        AnnotationType paragraphType = registry.getType("paragraph");
        AnnotationType cityType = registry.getType("city");

        AnnotationReferenceDataType annRefType =
            (AnnotationReferenceDataType)
            docMan.getDataType("annotationreference<text>");

        Struct position = new Struct(positionType);
        position.setFieldValue("latitude", new DoubleFieldValue(37.774929));
        position.setFieldValue("longitude", new DoubleFieldValue(-122.419415));

        Annotation sanAnnotation = new Annotation(textType);
        Annotation franciscoAnnotation = new Annotation(textType);

        Struct positionWithRef = cityDataType.createFieldValue();
        positionWithRef.setFieldValue("position", position);

        Field referencesField = cityDataType.getField("references");
        Array<FieldValue> refList =
            new Array<FieldValue>(referencesField.getDataType());
        refList.add(new AnnotationReference(annRefType, sanAnnotation));
        refList.add(new AnnotationReference(annRefType, franciscoAnnotation));
        positionWithRef.setFieldValue(referencesField, refList);

        Annotation city = new Annotation(cityType, positionWithRef);

        AlternateSpanList paragraph = new AlternateSpanList();
        paragraph.addChildren(new ArrayList<SpanNode>(), 0);
        paragraph.setProbability(0, 0.9);
        paragraph.setProbability(1, 0.1);
        {
            Span span1 = new Span(6, 3);
            Span span2 = new Span(9, 10);
            Span span3 = new Span(19, 4);
            Span span4 = new Span(23, 4);
            paragraph.add(0, span1)
                .add(0, span2)
                .add(0, span3)
                .add(0, span4);

            Span alt_span1 = new Span(6, 13);
            Span alt_span2 = new Span(19, 8);
            paragraph.add(1, alt_span1)
                .add(1, alt_span2);

            tree.annotate(span1, beginTag)
                .annotate(span2, textType)
                .annotate(span3, sanAnnotation)
                .annotate(span4, endTag)
                .annotate(alt_span1, textType)
                .annotate(alt_span2, bodyType)
                .annotate(paragraph, paragraphType);
        }

        {
            Span span1 = new Span(0, 6);
            Span span2 = new Span(27, 9);
            Span span3 = new Span(36, 8);
            root.add(span1)
                .add(paragraph)
                .add(span2)
                .add(span3);

            tree.annotate(span1, beginTag)
                .annotate(span2, franciscoAnnotation)
                .annotate(span3, endTag)
                .annotate(root, bodyType)
                .annotate(city);
        }

        StringFieldValue value = new StringFieldValue("lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj " +
                                                      "lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj l jlkj lkj lkj " +
                                                      "lkjoijoij oij oij oij oij oij oijoijoij oij oij oij oij oij " +
                                                      "oijoijoijoijoijoijoijoijoijoijoijoijoij oij oij oij oij " +
                                                      "oijaosdifjoai fdoais jdoasi jai os oafoai ai dfojsfoa dfoi dsf" +
                                                      "aosifjasofija sodfij oasdifj aosdiosifjsi ooai oais osi");
        value.setSpanTree(tree);

        //important! call readFile() before writeFile()!
        ByteBuffer serializedFromFile = readFile("test_data_serialized_advanced");
        ByteBuffer serialized = writeFile(value, "test_data_serialized_advanced");
        assertEquals(serialized.limit(), serializedFromFile.limit());

        StringFieldValue valueFromFile = new StringFieldValue();
        DocumentDeserializer deserializer = DocumentDeserializerFactory.create42(docMan, new GrowableByteBuffer(serializedFromFile));
        deserializer.read(null, valueFromFile);
        assertEquals(value, valueFromFile);
    }

    private static ByteBuffer writeFile(StringFieldValue value, String fileName) throws IOException {
        fileName = PATH + fileName;

        //serialize our tree to buffer
        VespaDocumentSerializer42 serializer = new VespaDocumentSerializer42();
        serializer.write(null, value);
        ByteBuffer serializedBuf = serializer.getBuf().getByteBuffer();
        serializedBuf.flip();

        //write our tree to disk
        File file = new File(fileName);
        FileChannel wChannel = new FileOutputStream(file, false).getChannel();
        wChannel.write(serializedBuf);
        wChannel.close();

        serializedBuf.position(0);
        return serializedBuf;
    }

    private static ByteBuffer readFile(String fileName) throws IOException {
        fileName = PATH + fileName;

        //read tree from disk
        ReadableByteChannel channel = new FileInputStream(fileName).getChannel();
        ByteBuffer readBuf = ByteBuffer.allocate(4096);
        channel.read(readBuf);
        readBuf.flip();
        channel.close();

        return readBuf;
    }

}
