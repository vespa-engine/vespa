// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class Bug6425939TestCase {
    private DocumentTypeManager man;
    private StructDataType person;
    private AnnotationType personA;

    @Before
    public void setUp() {
        man = new DocumentTypeManager();

        person = new StructDataType("personStruct");
        person.addField(new Field("foo", DataType.STRING));
        person.addField(new Field("bar", DataType.INT));
        man.register(person);

        personA = new AnnotationType("person", person);
        man.getAnnotationTypeRegistry().register(personA);
    }

    @Test
    public void canDeserializeAnnotationsOnZeroLengthStrings() {
        StringFieldValue emptyString = new StringFieldValue("");
        emptyString.setSpanTree(createSpanTree());

        GrowableByteBuffer buffer = new GrowableByteBuffer(1024);
        DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);
        Field strField = new Field("flarn", DataType.STRING);
        serializer.write(strField, emptyString);
        buffer.flip();

        // Should not throw exception if bug 6425939 is fixed:
        DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(man, buffer);
        StringFieldValue deserializedString = new StringFieldValue();
        deserializer.read(strField, deserializedString);

        assertEquals("", deserializedString.getString());
        SpanTree readTree = deserializedString.getSpanTree("SpanTree1");
        assertNotNull(readTree);
    }

    private SpanTree createSpanTree() {
        SpanList root = new SpanList();
        SpanTree tree = new SpanTree("SpanTree1", root);
        SpanNode node = new Span(0, 0);
        Struct ps = new Struct(person);
        ps.setFieldValue("foo", "epic badger");
        ps.setFieldValue("bar", 54321);
        tree.annotate(node, new Annotation(personA, ps));
        root.add(node);
        return tree;
    }
}
