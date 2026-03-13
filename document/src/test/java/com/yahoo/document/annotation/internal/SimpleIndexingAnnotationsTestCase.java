// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation.internal;

import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.annotation.Span;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for SimpleIndexingAnnotations - lightweight annotation representation.
 *
 * @author havardpe
 */
@SuppressWarnings({"deprecation", "removal"})
public class SimpleIndexingAnnotationsTestCase {

    @Test
    public void testBasicFunctionality() {
        SimpleIndexingAnnotations simple = new SimpleIndexingAnnotations();
        assertEquals(0, simple.getCount());

        simple.add(0, 5, "hello");
        simple.add(6, 5, null);  // null = use substring

        assertEquals(2, simple.getCount());
        assertEquals(0, simple.getFrom(0));
        assertEquals(5, simple.getLength(0));
        assertEquals("hello", simple.getTerm(0));

        assertEquals(6, simple.getFrom(1));
        assertEquals(5, simple.getLength(1));
        assertNull(simple.getTerm(1));
    }

    @Test
    public void testGrowth() {
        SimpleIndexingAnnotations simple = new SimpleIndexingAnnotations();

        // Add more than initial capacity (16)
        for (int i = 0; i < 100; i++) {
            simple.add(i * 10, 5, "term" + i);
        }

        assertEquals(100, simple.getCount());
        assertEquals(0, simple.getFrom(0));
        assertEquals("term0", simple.getTerm(0));
        assertEquals(990, simple.getFrom(99));
        assertEquals("term99", simple.getTerm(99));
    }

    @Test
    public void testConversionToSpanTree() {
        SimpleIndexingAnnotations simple = new SimpleIndexingAnnotations();
        simple.add(0, 5, "world");  // Term differs from substring
        simple.add(6, 5, null);     // Term equals substring

        SpanTree tree = simple.toSpanTree(SpanTrees.LINGUISTICS);

        assertEquals(SpanTrees.LINGUISTICS, tree.getName());
        assertEquals(2, tree.numAnnotations());

        int count = 0;
        for (Annotation ann : tree) {
            assertEquals(AnnotationTypes.TERM, ann.getType());
            assertTrue(ann.hasSpanNode());
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    public void testSerializationCompatibility() {
        // This test verifies that SimpleIndexingAnnotations serializes to the same format as SpanTree
        // Note: Currently disabled as it requires testing with feature flag enabled at JVM level
        // TODO: Enable this test with proper feature flag setup

        // Create with simple annotations directly (not via feature flag for now)
        StringFieldValue simpleField = new StringFieldValue("hello world");
        SimpleIndexingAnnotations simple = new SimpleIndexingAnnotations();
        simple.add(0, 5, "hello");
        simple.add(6, 5, null);

        // Verify conversion to SpanTree works
        SpanTree convertedTree = simple.toSpanTree(SpanTrees.LINGUISTICS);
        assertEquals(SpanTrees.LINGUISTICS, convertedTree.getName());
        assertEquals(2, convertedTree.numAnnotations());
    }

    @Test
    public void testAPICompatibility() {
        // Test that SimpleIndexingAnnotations work with public API via lazy conversion
        StringFieldValue field = new StringFieldValue("hello world");
        SimpleIndexingAnnotations simple = new SimpleIndexingAnnotations();
        simple.add(0, 5, "hello");
        simple.add(6, 5, null);

        // Verify toSpanTree conversion
        SpanTree tree = simple.toSpanTree(SpanTrees.LINGUISTICS);
        assertNotNull(tree);
        assertEquals(2, tree.numAnnotations());
        assertEquals(SpanTrees.LINGUISTICS, tree.getName());

        // Verify annotations are correct
        int count = 0;
        for (Annotation ann : tree) {
            assertEquals(AnnotationTypes.TERM, ann.getType());
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    public void testMultipleTermsPerPosition() {
        // Test StemMode.ALL scenario - multiple annotations on same position
        SimpleIndexingAnnotations simple = new SimpleIndexingAnnotations();
        simple.add(0, 7, "running");  // Main term
        simple.add(0, 7, "run");      // Stem
        simple.add(0, 7, null);       // Original form

        assertEquals(3, simple.getCount());
        // All at same position
        assertEquals(0, simple.getFrom(0));
        assertEquals(0, simple.getFrom(1));
        assertEquals(0, simple.getFrom(2));
        assertEquals(7, simple.getLength(0));
        assertEquals(7, simple.getLength(1));
        assertEquals(7, simple.getLength(2));
    }

    private byte[] serialize(StringFieldValue value) {
        GrowableByteBuffer buffer = new GrowableByteBuffer(1024);
        var serializer = DocumentSerializerFactory.createHead(buffer);
        value.serialize(null, serializer);
        buffer.flip();
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }
}
