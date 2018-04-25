// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

/**
 * @author Einar M R Rosenvinge
 */
public class SystemTestCase {

    DocumentTypeManager manager;

    private void annotate(Document document) {
        AnnotationTypeRegistry registry = manager.getAnnotationTypeRegistry();
        AnnotationType personType = registry.getType("person");
        AnnotationType artistType = registry.getType("artist");
        AnnotationType dateType = registry.getType("date");
        AnnotationType placeType = registry.getType("place");
        AnnotationType eventType = registry.getType("event");

        SpanList root = new SpanList();
        SpanTree tree = new SpanTree("meaningoflife", root);

        SpanNode personSpan = new Span(0,5);
        SpanNode artistSpan = new Span(5,10);
        SpanNode dateSpan = new Span(10,15);
        SpanNode placeSpan = new Span(15,20);

        root.add(personSpan);
        root.add(artistSpan);
        root.add(dateSpan);
        root.add(placeSpan);

        Struct personValue = new Struct(manager.getDataType("annotation.person"));
        personValue.setFieldValue("name", "george washington");
        Annotation person = new Annotation(personType, personValue);
        tree.annotate(personSpan, person);

        Struct artistValue = new Struct(manager.getDataType("annotation.artist"));
        artistValue.setFieldValue("name", "elvis presley");
        artistValue.setFieldValue("instrument", 20);
        Annotation artist = new Annotation(artistType, artistValue);
        tree.annotate(artistSpan, artist);

        Struct dateValue = new Struct(manager.getDataType("annotation.date"));
        dateValue.setFieldValue("exacttime", 123456789L);
        Annotation date = new Annotation(dateType, dateValue);
        tree.annotate(dateSpan, date);

        Struct placeValue = new Struct(manager.getDataType("annotation.place"));
        placeValue.setFieldValue("lat", 1467L);
        placeValue.setFieldValue("lon", 789L);
        Annotation place = new Annotation(placeType, placeValue);
        tree.annotate(placeSpan, place);

        Struct eventValue = new Struct(manager.getDataType("annotation.event"));
        eventValue.setFieldValue("description", "Big concert");
        eventValue.setFieldValue("person", new AnnotationReference((AnnotationReferenceDataType) manager.getDataType("annotationreference<person>"), person));
        eventValue.setFieldValue("date", new AnnotationReference((AnnotationReferenceDataType) manager.getDataType("annotationreference<date>"), date));
        eventValue.setFieldValue("place", new AnnotationReference((AnnotationReferenceDataType) manager.getDataType("annotationreference<place>"), place));
        Annotation event = new Annotation(eventType, eventValue);
        tree.annotate(root, event);

        StringFieldValue content = new StringFieldValue("This is the story of a big concert by Elvis and a special guest appearance by George Washington");
        content.setSpanTree(tree);

        document.setFieldValue(document.getDataType().getField("content"), content);
    }

    private void consume(Document document) {
        StringFieldValue content = (StringFieldValue) document.getFieldValue(document.getDataType().getField("content"));

        SpanTree tree = content.getSpanTree("meaningoflife");
        SpanList root = (SpanList) tree.getRoot();

        Iterator<SpanNode> childIterator = root.childIterator();
        SpanNode personSpan = childIterator.next();
        SpanNode artistSpan = childIterator.next();
        SpanNode dateSpan = childIterator.next();
        SpanNode placeSpan = childIterator.next();

        Annotation person = tree.iterator(personSpan).next();
        Struct personValue = (Struct) person.getFieldValue();
        System.err.println("Person is " + personValue.getField("name"));

        Annotation artist = tree.iterator(artistSpan).next();
        Struct artistValue = (Struct) artist.getFieldValue();
        System.err.println("Artist is " + artistValue.getFieldValue("name") + " who plays the " + artistValue.getFieldValue("instrument"));

        Annotation date = tree.iterator(dateSpan).next();
        Struct dateValue = (Struct) date.getFieldValue();
        System.err.println("Date is " + dateValue.getFieldValue("exacttime"));

        Annotation place = tree.iterator(placeSpan).next();
        Struct placeValue = (Struct) place.getFieldValue();
        System.err.println("Place is " + placeValue.getFieldValue("lat") + ";" + placeValue.getFieldValue("lon"));

        Annotation event = tree.iterator(root).next();
        Struct eventValue = (Struct) event.getFieldValue();
        System.err.println("Event is " + eventValue.getFieldValue("description") + " with " + eventValue.getFieldValue("person") + " and " + eventValue.getFieldValue("date") + " and " + eventValue.getFieldValue("place"));
    }

    @Before
    public void setUp() {
        manager = new DocumentTypeManager();
        DocumentTypeManagerConfigurer.configure(manager, "file:src/test/java/com/yahoo/document/annotation/documentmanager.systemtest.cfg");
    }

    @Test
    public void testSystemTest() {
        DocumentType type = manager.getDocumentType("article");
        Document inDocument = new Document(type, "doc:article:boringarticle:longarticle");
        annotate(inDocument);

        GrowableByteBuffer buffer = new GrowableByteBuffer();
        DocumentSerializer serializer = DocumentSerializerFactory.create42(buffer);
        serializer.write(inDocument);
        buffer.flip();
        DocumentDeserializer deserializer = DocumentDeserializerFactory.create42(manager, buffer);

        Document outDocument = new Document(deserializer);
        consume(outDocument);
    }

}
