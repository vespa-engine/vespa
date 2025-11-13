// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.annotation.SimpleIndexingAnnotations;
import com.yahoo.document.annotation.Span;
import com.yahoo.document.annotation.SpanList;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

/**
 * Tests that serialization of SimpleIndexingAnnotations produces the same
 * semantic output as full SpanTree serialization, and that round-trip
 * serialization/deserialization preserves semantic contents.
 *
 * @author arnej
 */
public class SimpleAnnotationsSerializationTestCase {

    private static final DocumentTypeManager docMan = new DocumentTypeManager();
    private static final DocumentType DOC_TYPE = new DocumentType("test");
    private static final Field STRING_FIELD = new Field("text", DataType.STRING);

    static {
        DOC_TYPE.addField(STRING_FIELD);
        docMan.registerDocumentType(DOC_TYPE);
    }

    /**
     * Helper to serialize and deserialize a StringFieldValue
     */
    private StringFieldValue serializeAndDeserialize(StringFieldValue original) {
        GrowableByteBuffer buffer = new GrowableByteBuffer();
        DocumentSerializer serializer = DocumentSerializerFactory.createHead(buffer);
        serializer.write(null, original);
        buffer.flip();

        DocumentDeserializer deserializer = DocumentDeserializerFactory.createHead(docMan, buffer);
        StringFieldValue result = new StringFieldValue();
        deserializer.read(null, result);

        return result;
    }

    /**
     * Helper to create a StringFieldValue with simple annotations
     */
    private StringFieldValue createWithSimpleAnnotations(String text, int[][] annotations) {
        StringFieldValue value = new StringFieldValue(text);
        SimpleIndexingAnnotations simple = value.createSimpleAnnotations();
        assertNotNull("Simple annotations should be enabled", simple);

        for (int[] ann : annotations) {
            int from = ann[0];
            int length = ann[1];
            String term = ann.length > 2 ? text.substring(ann[2], ann[2] + ann[3]) : null;
            simple.add(from, length, term);
        }

        return value;
    }

    /**
     * Helper to create a StringFieldValue with full SpanTree
     */
    private StringFieldValue createWithSpanTree(String text, int[][] annotations) {
        StringFieldValue value = new StringFieldValue(text);
        SpanTree tree = new SpanTree(SpanTrees.LINGUISTICS);
        tree.setStringFieldValue(value);

        for (int[] ann : annotations) {
            int from = ann[0];
            int length = ann[1];
            Span span = tree.spanList().span(from, length);

            if (ann.length > 2) {
                String term = text.substring(ann[2], ann[2] + ann[3]);
                tree.annotate(span, new Annotation(AnnotationTypes.TERM, new StringFieldValue(term)));
            } else {
                tree.annotate(span, new Annotation(AnnotationTypes.TERM));
            }
        }

        value.setSpanTree(tree);
        return value;
    }

    /**
     * Compare semantic contents of two StringFieldValues
     */
    private void assertSemanticallyEqual(String message, StringFieldValue expected, StringFieldValue actual) {
        assertEquals(message + ": text differs", expected.getString(), actual.getString());

        SpanTree expectedTree = expected.getSpanTree(SpanTrees.LINGUISTICS);
        SpanTree actualTree = actual.getSpanTree(SpanTrees.LINGUISTICS);

        if (expectedTree == null) {
            assertNull(message + ": expected no annotations", actualTree);
            return;
        }

        assertNotNull(message + ": expected annotations", actualTree);

        // Compare span tree contents
        assertEquals(message + ": tree name differs", expectedTree.getName(), actualTree.getName());

        // Get all annotations and compare
        var expectedAnnotations = new java.util.ArrayList<Annotation>();
        for (var ann : expectedTree) {
            expectedAnnotations.add(ann);
        }

        var actualAnnotations = new java.util.ArrayList<Annotation>();
        for (var ann : actualTree) {
            actualAnnotations.add(ann);
        }

        assertEquals(message + ": annotation count differs",
                     expectedAnnotations.size(), actualAnnotations.size());

        for (int i = 0; i < expectedAnnotations.size(); i++) {
            Annotation expAnn = expectedAnnotations.get(i);
            Annotation actAnn = actualAnnotations.get(i);

            assertEquals(message + ": annotation " + i + " type differs",
                        expAnn.getType(), actAnn.getType());

            // Compare span positions
            if (expAnn.getSpanNode() instanceof Span expSpan && actAnn.getSpanNode() instanceof Span actSpan) {
                assertEquals(message + ": annotation " + i + " span from differs",
                            expSpan.getFrom(), actSpan.getFrom());
                assertEquals(message + ": annotation " + i + " span length differs",
                            expSpan.getLength(), actSpan.getLength());
            }

            // Compare field values
            FieldValue expValue = expAnn.getFieldValue();
            FieldValue actValue = actAnn.getFieldValue();

            if (expValue == null) {
                assertNull(message + ": annotation " + i + " should have no value", actValue);
            } else {
                assertNotNull(message + ": annotation " + i + " should have value", actValue);
                assertEquals(message + ": annotation " + i + " value differs",
                            expValue.toString(), actValue.toString());
            }
        }
    }

    @Test
    public void testSimpleAnnotationsRoundTrip() {
        System.setProperty("vespa.indexing.simple_annotations", "true");
        try {
            // Test with simple single annotation
            int[][] annotations = {{0, 5}};  // "hello" at position 0, length 5
            StringFieldValue original = createWithSimpleAnnotations("hello world", annotations);

            StringFieldValue roundTrip = serializeAndDeserialize(original);

            assertSemanticallyEqual("Simple round-trip", original, roundTrip);
        } finally {
            System.clearProperty("vespa.indexing.simple_annotations");
        }
    }

    @Test
    public void testSpanTreeRoundTrip() {
        System.setProperty("vespa.indexing.simple_annotations", "false");
        try {
            // Test with full SpanTree
            int[][] annotations = {{0, 5}, {6, 5}};  // "hello" and "world"
            StringFieldValue original = createWithSpanTree("hello world", annotations);

            StringFieldValue roundTrip = serializeAndDeserialize(original);

            assertSemanticallyEqual("SpanTree round-trip", original, roundTrip);
        } finally {
            System.clearProperty("vespa.indexing.simple_annotations");
        }
    }

    @Test
    public void testSimpleAnnotationsWithTermOverride() {
        System.setProperty("vespa.indexing.simple_annotations", "true");
        try {
            // Test with term override (stemming)
            int[][] annotations = {
                {0, 6, 0, 3},   // "runner" -> "run"
                {7, 4}          // "fast" -> no override
            };
            StringFieldValue original = createWithSimpleAnnotations("runner fast", annotations);

            StringFieldValue roundTrip = serializeAndDeserialize(original);

            assertSemanticallyEqual("Simple with term override", original, roundTrip);
        } finally {
            System.clearProperty("vespa.indexing.simple_annotations");
        }
    }

    @Test
    public void testMultipleAnnotationsSameSpan() {
        System.setProperty("vespa.indexing.simple_annotations", "true");
        try {
            // Test multiple annotations on same span (stemming mode ALL)
            // "Teslas" with stems ["tesla", "teslas"]
            StringFieldValue value = new StringFieldValue("Teslas");
            SimpleIndexingAnnotations simple = value.createSimpleAnnotations();
            assertNotNull(simple);

            simple.add(0, 6, "tesla");   // First stem
            simple.add(0, 6, "teslas");  // Second stem - SAME SPAN

            StringFieldValue roundTrip = serializeAndDeserialize(value);

            assertSemanticallyEqual("Multiple annotations same span", value, roundTrip);

            // Verify both annotations exist and reference the same span
            SpanTree tree = roundTrip.getSpanTree(SpanTrees.LINGUISTICS);
            assertNotNull(tree);

            var annotations = new java.util.ArrayList<Annotation>();
            for (var ann : tree) {
                annotations.add(ann);
            }

            assertEquals("Should have 2 annotations", 2, annotations.size());

            // Both should reference spans at the same position
            Span span1 = (Span) annotations.get(0).getSpanNode();
            Span span2 = (Span) annotations.get(1).getSpanNode();

            assertEquals("Same span position", span1.getFrom(), span2.getFrom());
            assertEquals("Same span length", span1.getLength(), span2.getLength());
        } finally {
            System.clearProperty("vespa.indexing.simple_annotations");
        }
    }

    @Test
    public void testBothModesProduceSameOutput() {
        // Create same annotations in both modes and verify serialized output is semantically equivalent
        String text = "hello world";
        int[][] annotations = {{0, 5, 0, 4}, {6, 5}};  // "hello"->"hell", "world"

        System.setProperty("vespa.indexing.simple_annotations", "true");
        StringFieldValue simpleVersion;
        try {
            simpleVersion = createWithSimpleAnnotations(text, annotations);
        } finally {
            System.clearProperty("vespa.indexing.simple_annotations");
        }

        System.setProperty("vespa.indexing.simple_annotations", "false");
        StringFieldValue spanTreeVersion;
        try {
            spanTreeVersion = createWithSpanTree(text, annotations);
        } finally {
            System.clearProperty("vespa.indexing.simple_annotations");
        }

        // Serialize both
        StringFieldValue simpleRoundTrip = serializeAndDeserialize(simpleVersion);
        StringFieldValue spanTreeRoundTrip = serializeAndDeserialize(spanTreeVersion);

        // Both should be semantically equivalent
        assertSemanticallyEqual("Simple vs SpanTree mode", simpleRoundTrip, spanTreeRoundTrip);
    }

    @Test
    public void testEmptyAnnotations() {
        System.setProperty("vespa.indexing.simple_annotations", "true");
        try {
            StringFieldValue value = new StringFieldValue("hello world");
            // No annotations added

            StringFieldValue roundTrip = serializeAndDeserialize(value);

            assertSemanticallyEqual("Empty annotations", value, roundTrip);
            assertNull("Should have no span tree", roundTrip.getSpanTree(SpanTrees.LINGUISTICS));
        } finally {
            System.clearProperty("vespa.indexing.simple_annotations");
        }
    }
}
