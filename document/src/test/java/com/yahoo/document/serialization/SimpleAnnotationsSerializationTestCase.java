// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.annotation.internal.SimpleIndexingAnnotations;
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
@SuppressWarnings({"deprecation", "removal"})
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
        assertTrue("Simple annotations should be enabled", value.wantSimpleAnnotations());
        SimpleIndexingAnnotations simple = new SimpleIndexingAnnotations();
        for (int[] ann : annotations) {
            int from = ann[0];
            int length = ann[1];
            String term = ann.length > 2 ? text.substring(ann[2], ann[2] + ann[3]) : null;
            simple.add(from, length, term);
        }
        value.setSimpleAnnotations(simple);
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
        com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(true);
        try {
            // Test with simple single annotation
            int[][] annotations = {{0, 5}};  // "hello" at position 0, length 5
            StringFieldValue original = createWithSimpleAnnotations("hello world", annotations);

            StringFieldValue roundTrip = serializeAndDeserialize(original);

            // Verify that we got simple annotations back after deserialization - CHECK THIS FIRST
            // before calling getSpanTree() which would trigger conversion
            SimpleIndexingAnnotations simple = roundTrip.getSimpleAnnotations();
            assertNotNull("Should have simple annotations after deserialization", simple);
            assertEquals("Should have 1 annotation", 1, simple.getCount());
            assertEquals("First annotation from", 0, simple.getFrom(0));
            assertEquals("First annotation length", 5, simple.getLength(0));
            assertNull("First annotation term should be null (equals substring)", simple.getTerm(0));

            assertSemanticallyEqual("Simple round-trip", original, roundTrip);
        } finally {
            com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        }
    }

    @Test
    public void testSpanTreeRoundTrip() {
        com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        try {
            // Test with full SpanTree
            int[][] annotations = {{0, 5}, {6, 5}};  // "hello" and "world"
            StringFieldValue original = createWithSpanTree("hello world", annotations);

            StringFieldValue roundTrip = serializeAndDeserialize(original);

            assertSemanticallyEqual("SpanTree round-trip", original, roundTrip);
        } finally {
            com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        }
    }

    @Test
    public void testSimpleAnnotationsWithTermOverride() {
        com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(true);
        try {
            // Test with term override (stemming)
            int[][] annotations = {
                {0, 6, 0, 3},   // "runner" -> "run"
                {7, 4}          // "fast" -> no override
            };
            StringFieldValue original = createWithSimpleAnnotations("runner fast", annotations);

            StringFieldValue roundTrip = serializeAndDeserialize(original);

            // Verify that we got simple annotations back after deserialization - CHECK THIS FIRST
            SimpleIndexingAnnotations simple = roundTrip.getSimpleAnnotations();
            assertNotNull("Should have simple annotations after deserialization", simple);
            assertEquals("Should have 2 annotations", 2, simple.getCount());
            assertEquals("First annotation from", 0, simple.getFrom(0));
            assertEquals("First annotation length", 6, simple.getLength(0));
            assertEquals("First annotation term", "run", simple.getTerm(0));
            assertEquals("Second annotation from", 7, simple.getFrom(1));
            assertEquals("Second annotation length", 4, simple.getLength(1));
            assertNull("Second annotation term should be null", simple.getTerm(1));

            assertSemanticallyEqual("Simple with term override", original, roundTrip);
        } finally {
            com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        }
    }

    @Test
    public void testMultipleAnnotationsSameSpan() {
        com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(true);
        try {
            // Test multiple annotations on same span (stemming mode ALL)
            // "Teslas" with stems ["tesla", "teslas"]
            StringFieldValue value = new StringFieldValue("Teslas");
            assertTrue("Simple annotations should be enabled", value.wantSimpleAnnotations());
            SimpleIndexingAnnotations simple = new SimpleIndexingAnnotations();
            simple.add(0, 6, "tesla");   // First stem
            simple.add(0, 6, "teslas");  // Second stem - SAME SPAN
            value.setSimpleAnnotations(simple);

            StringFieldValue roundTrip = serializeAndDeserialize(value);

            // Verify that we got simple annotations back after deserialization - CHECK THIS FIRST
            SimpleIndexingAnnotations simpleRoundTrip = roundTrip.getSimpleAnnotations();
            assertNotNull("Should have simple annotations after deserialization", simpleRoundTrip);
            assertEquals("Should have 2 annotations", 2, simpleRoundTrip.getCount());
            assertEquals("First annotation from", 0, simpleRoundTrip.getFrom(0));
            assertEquals("First annotation length", 6, simpleRoundTrip.getLength(0));
            assertEquals("First annotation term", "tesla", simpleRoundTrip.getTerm(0));
            assertEquals("Second annotation from", 0, simpleRoundTrip.getFrom(1));
            assertEquals("Second annotation length", 6, simpleRoundTrip.getLength(1));
            assertEquals("Second annotation term", "teslas", simpleRoundTrip.getTerm(1));

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
            com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        }
    }

    @Test
    public void testBothModesProduceSameOutput() {
        // Create same annotations in both modes and verify serialized output is semantically equivalent
        String text = "hello world";
        int[][] annotations = {{0, 5, 0, 4}, {6, 5}};  // "hello"->"hell", "world"

        com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(true);
        StringFieldValue simpleVersion;
        try {
            simpleVersion = createWithSimpleAnnotations(text, annotations);
        } finally {
            com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        }

        com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        StringFieldValue spanTreeVersion;
        try {
            spanTreeVersion = createWithSpanTree(text, annotations);
        } finally {
            com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        }

        // Serialize both
        com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(true);
        StringFieldValue simpleRoundTrip;
        try {
            simpleRoundTrip = serializeAndDeserialize(simpleVersion);

            // Verify that simple mode preserved simple annotations - CHECK THIS FIRST
            SimpleIndexingAnnotations simple = simpleRoundTrip.getSimpleAnnotations();
            assertNotNull("Should have simple annotations after deserialization", simple);
            assertEquals("Should have 2 annotations", 2, simple.getCount());
            assertEquals("First annotation from", 0, simple.getFrom(0));
            assertEquals("First annotation length", 5, simple.getLength(0));
            assertEquals("First annotation term", "hell", simple.getTerm(0));
            assertEquals("Second annotation from", 6, simple.getFrom(1));
            assertEquals("Second annotation length", 5, simple.getLength(1));
            assertNull("Second annotation term should be null", simple.getTerm(1));
        } finally {
            com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        }

        StringFieldValue spanTreeRoundTrip = serializeAndDeserialize(spanTreeVersion);

        // Both should be semantically equivalent
        assertSemanticallyEqual("Simple vs SpanTree mode", simpleRoundTrip, spanTreeRoundTrip);
    }

    @Test
    public void testEmptyAnnotations() {
        com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(true);
        try {
            StringFieldValue value = new StringFieldValue("hello world");
            // No annotations added

            StringFieldValue roundTrip = serializeAndDeserialize(value);

            assertSemanticallyEqual("Empty annotations", value, roundTrip);
            assertNull("Should have no span tree", roundTrip.getSpanTree(SpanTrees.LINGUISTICS));
        } finally {
            com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        }
    }

    @Test
    public void testAnnotationReferencingLastSpan() {
        com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(true);
        try {
            // Create annotations using SimpleIndexingAnnotations directly
            // This ensures we're testing the serialization path that uses simple annotations
            String text = "one two three";
            int[][] annotations = {
                {0, 3},    // "one" - will be span index 1
                {4, 3},    // "two" - will be span index 2
                {8, 5}     // "three" - will be span index 3 (LAST span, this triggers the bug!)
            };
            StringFieldValue original = createWithSimpleAnnotations(text, annotations);

            // Serialize and deserialize
            StringFieldValue roundTrip = serializeAndDeserialize(original);

            // Verify that the deserialization succeeded and we got simple annotations back
            // Without the fix, the annotation referencing the last span (index 3) would be rejected
            // during deserialization, causing fallback to full SpanTree deserialization,
            // and simple would be null here
            SimpleIndexingAnnotations simple = roundTrip.getSimpleAnnotations();
            assertNotNull("Should have simple annotations after deserialization - " +
                         "if null, the off-by-one bug caused deserialization to reject the last annotation", simple);
            assertEquals("Should have 3 annotations", 3, simple.getCount());

            // Verify all three annotations are present, especially the last one
            assertEquals("First annotation from", 0, simple.getFrom(0));
            assertEquals("First annotation length", 3, simple.getLength(0));

            assertEquals("Second annotation from", 4, simple.getFrom(1));
            assertEquals("Second annotation length", 3, simple.getLength(1));

            // This is the critical test - the annotation referencing the last span
            // Without the fix (spanIndex < numSpans), this annotation would be rejected
            assertEquals("Third annotation from (LAST SPAN)", 8, simple.getFrom(2));
            assertEquals("Third annotation length (LAST SPAN)", 5, simple.getLength(2));

            // Verify semantic equivalence
            assertSemanticallyEqual("Annotation referencing last span", original, roundTrip);
        } finally {
            com.yahoo.document.annotation.internal.SimpleIndexingAnnotations.setEnabled(false);
        }
    }
}
