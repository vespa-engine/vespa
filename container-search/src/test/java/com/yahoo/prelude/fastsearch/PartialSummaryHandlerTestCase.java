// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.Schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author arnej
 */
public class PartialSummaryHandlerTestCase {

    @Test
    void testFillNull() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery(null);
        var hit1 = createHit(query);
        var hit2 = createHit(query, (String)null);
        var result = createResult(query, hit1, hit2);
        var toTest = new PartialSummaryHandler(set);
        toTest.wantToFill(result, null);
        assertEquals("default", toTest.askForSummary());
        assertNull(toTest.askForFields());
        assertFalse(toTest.resultAlreadyFilled());
        assertTrue(toTest.needFill(hit1));
        assertFalse(toTest.needFill(hit2));
        var docsumDef = toTest.effectiveDocsumDef();
        assertNotNull(docsumDef);
        assertEquals(6, docsumDef.fields().size());
        assertFalse(hit1.isFilled(null));
        toTest.markFilled(hit1);
        assertTrue(hit1.isFilled(null));
        assertTrue(hit1.isFilled("default"));
        assertEquals(1, result.hits().getFilled().size());
    }

    @Test
    void testFillDefault() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery("default");
        var hit1 = createHit(query);
        var hit2 = createHit(query, "default");
        var result = createResult(query, hit1, hit2);
        var toTest = new PartialSummaryHandler(set);
        toTest.wantToFill(result, "default");
        assertEquals("default", toTest.askForSummary());
        assertNull(toTest.askForFields());
        assertFalse(toTest.resultAlreadyFilled());
        assertTrue(toTest.needFill(hit1));
        assertFalse(toTest.needFill(hit2));
        var docsumDef = toTest.effectiveDocsumDef();
        assertNotNull(docsumDef);
        assertEquals(6, docsumDef.fields().size());
        assertFalse(hit1.isFilled("default"));
        toTest.markFilled(hit1);
        assertFalse(hit1.isFilled(null));
        assertTrue(hit1.isFilled("default"));
        assertEquals(1, result.hits().getFilled().size());
    }

    @Test
    void testFillOneSimple() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery(null);
        var hit1 = createHit(query);
        var hit2 = createHit(query, "middle2");
        var result = createResult(query, hit1, hit2);
        var toTest = new PartialSummaryHandler(set);
        toTest.wantToFill(result, "middle2");
        assertEquals("middle2", toTest.askForSummary());
        assertNull(toTest.askForFields());
        assertFalse(toTest.resultAlreadyFilled());
        assertTrue(toTest.needFill(hit1));
        assertFalse(toTest.needFill(hit2));
        var docsumDef = toTest.effectiveDocsumDef();
        assertNotNull(docsumDef);
        assertEquals(2, docsumDef.fields().size());
        assertFalse(hit1.isFilled("middle2"));
        toTest.markFilled(hit1);
        assertTrue(hit1.isFilled("middle2"));
        assertEquals(1, result.hits().getFilled().size());
    }

    @Test
    void testFillSomeFields() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery(null);
        query.getPresentation().getSummaryFields().add("TOPIC");
        query.getPresentation().getSummaryFields().add("SCORE");
        query.getPresentation().getSummaryFields().add("CHUNK");
        query.getPresentation().getSummaryFields().add("BYTES");
        var hit1 = createHit(query);
        var hit2 = createHit(query, "default");
        var result = createResult(query, hit1, hit2);
        var toTest = new PartialSummaryHandler(set);
        toTest.wantToFill(result, null);
        assertEquals("default", toTest.askForSummary());
        assertNotNull(toTest.askForFields());
        assertEquals(4, toTest.askForFields().size());
        assertFalse(toTest.resultAlreadyFilled());
        assertTrue(toTest.needFill(hit1));
        assertFalse(toTest.needFill(hit2));
        var docsumDef = toTest.effectiveDocsumDef();
        assertNotNull(docsumDef);
        assertEquals(4, docsumDef.fields().size());
        assertFalse(hit1.isFilled("[f:BYTES,CHUNK,SCORE,TOPIC]"));
        toTest.markFilled(hit1);
        assertTrue(hit1.isFilled("[f:BYTES,CHUNK,SCORE,TOPIC]"));
        // no overlap
        assertEquals(0, result.hits().getFilled().size());
        // we will do this in dispatch code:
        toTest.markFilled(hit2);
        assertEquals(1, result.hits().getFilled().size());
    }

    @Test
    void testFillNotNeeded() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery(null);
        query.getPresentation().getSummaryFields().add("matchfeatures");
        var hit1 = createHit(query, "[f:matchfeatures]");
        var result = createResult(query, hit1);
        var toTest = new PartialSummaryHandler(set);
        toTest.wantToFill(result, null);
        assertTrue(toTest.resultAlreadyFilled());
        // below should probably be skipped when result is already filled:
        assertEquals("default", toTest.askForSummary());
        assertNotNull(toTest.askForFields());
        assertEquals(1, toTest.askForFields().size());
        var hit2 = createHit(query);
        toTest.markFilled(hit2);
        assertEquals(1, hit2.getFilled().size());
        assertEquals("[f:matchfeatures]", hit2.getFilled().iterator().next());
    }

    @Test
    void testMultiFill() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery("first3");
        var hit1 = createHit(query);
        var hit2 = createHit(query, "default");
        var result = createResult(query, hit1, hit2);
        {
            var toTest = new PartialSummaryHandler(set);
            toTest.wantToFill(result, query.getPresentation().getSummary());
            assertEquals("first3", toTest.askForSummary());
            assertNull(toTest.askForFields());
            assertFalse(toTest.resultAlreadyFilled());
            assertTrue(toTest.needFill(hit1));
            assertFalse(toTest.needFill(hit2));
            var docsumDef = toTest.effectiveDocsumDef();
            assertNotNull(docsumDef);
            assertEquals(3, docsumDef.fields().size());
            assertEquals(0, hit1.getFilled().size());
            toTest.markFilled(hit1);
            toTest.markFilled(hit2);
            assertTrue(hit1.isFilled("first3"));
        }
        {
            var toTest = new PartialSummaryHandler(set);
            toTest.wantToFill(result, "last3");
            assertEquals("last3", toTest.askForSummary());
            assertNull(toTest.askForFields());
            assertFalse(toTest.resultAlreadyFilled());
            assertTrue(toTest.needFill(hit1));
            assertFalse(toTest.needFill(hit2));
            var docsumDef = toTest.effectiveDocsumDef();
            assertNotNull(docsumDef);
            assertEquals(3, docsumDef.fields().size());
            assertEquals(1, hit1.getFilled().size());
            assertFalse(hit1.isFilled("last3"));
            toTest.markFilled(hit1);
            toTest.markFilled(hit2);
            assertEquals(2, hit1.getFilled().size());
            assertTrue(hit1.isFilled("last3"));
        }
        {
            var toTest = new PartialSummaryHandler(set);
            toTest.wantToFill(result, "middle2");
            assertTrue(toTest.resultAlreadyFilled());
        }
    }

    @Test
    void testFillMoreAfterPresentation() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery("first3");
        var hit1 = createHit(query);
        var hit2 = createHit(query, "default");
        var result = createResult(query, hit1, hit2);
        {
            var toTest = new PartialSummaryHandler(set);
            toTest.wantToFill(result, "[presentation]");
            assertEquals("first3", toTest.askForSummary());
            assertNull(toTest.askForFields());
            assertFalse(toTest.resultAlreadyFilled());
            assertTrue(toTest.needFill(hit1));
            assertFalse(toTest.needFill(hit2));
            var docsumDef = toTest.effectiveDocsumDef();
            assertNotNull(docsumDef);
            assertEquals(3, docsumDef.fields().size());
            assertEquals(0, hit1.getFilled().size());
            toTest.markFilled(hit1);
            toTest.markFilled(hit2);
            assertTrue(hit1.isFilled("first3"));
            assertTrue(hit1.isFilled("[presentation]"));
        }
        {
            var toTest = new PartialSummaryHandler(set);
            toTest.wantToFill(result, "last3");
            assertEquals("last3", toTest.askForSummary());
            assertNull(toTest.askForFields());
            assertFalse(toTest.resultAlreadyFilled());
            assertTrue(toTest.needFill(hit1));
            assertFalse(toTest.needFill(hit2));
            var docsumDef = toTest.effectiveDocsumDef();
            assertNotNull(docsumDef);
            assertEquals(3, docsumDef.fields().size());
            assertEquals(2, hit1.getFilled().size());
            assertFalse(hit1.isFilled("last3"));
            toTest.markFilled(hit1);
            toTest.markFilled(hit2);
            assertEquals(3, hit1.getFilled().size());
            assertTrue(hit1.isFilled("last3"));
        }
        {
            var toTest = new PartialSummaryHandler(set);
            toTest.wantToFill(result, "middle2");
            assertTrue(toTest.resultAlreadyFilled());
        }
    }


    @Test
    void testInvalidUsage() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery(null);
        var hit1 = createHit(query);
        var result = createResult(query, hit1);
        var e1 = assertThrows(IllegalArgumentException.class, () -> {
                var toTest = new PartialSummaryHandler(set);
                toTest.wantToFill(result, "badname");
                boolean got = toTest.resultAlreadyFilled();
            });
        assertEquals("unknown summary class: badname", e1.getMessage());
        var e2 = assertThrows(IllegalArgumentException.class, () -> {
                var toTest = new PartialSummaryHandler(set);
                toTest.wantToFill(result, "[bad]");
            });
        assertEquals("fill([bad]) is not valid", e2.getMessage());
    }

    @Test
    void testNullPresentation() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery(null);
        var hit1 = createHit(query);
        var hit2 = createHit(query, (String)null);
        var result = createResult(query, hit1, hit2);
        var toTest = new PartialSummaryHandler(set);
        toTest.wantToFill(result, PartialSummaryHandler.PRESENTATION);
        assertEquals("default", toTest.askForSummary());
        assertNull(toTest.askForFields());
        assertFalse(toTest.resultAlreadyFilled());
        assertTrue(toTest.needFill(hit1));
        assertFalse(toTest.needFill(hit2));
        var docsumDef = toTest.effectiveDocsumDef();
        assertNotNull(docsumDef);
        assertEquals(6, docsumDef.fields().size());
        assertFalse(hit1.isFilled(null));
        toTest.markFilled(hit1);
        assertTrue(hit1.isFilled(PartialSummaryHandler.PRESENTATION));
        assertTrue(hit1.isFilled(null));
        assertEquals(1, result.hits().getFilled().size());
    }

    @Test
    void testClassPresentation() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery("middle2");
        var hit1 = createHit(query);
        var hit2 = createHit(query, "default");
        var result = createResult(query, hit1, hit2);
        var toTest = new PartialSummaryHandler(set);
        toTest.wantToFill(result, PartialSummaryHandler.PRESENTATION);
        assertEquals("middle2", toTest.askForSummary());
        assertNull(toTest.askForFields());
        assertFalse(toTest.resultAlreadyFilled());
        assertTrue(toTest.needFill(hit1));
        assertFalse(toTest.needFill(hit2));
        var docsumDef = toTest.effectiveDocsumDef();
        assertNotNull(docsumDef);
        assertEquals(2, docsumDef.fields().size());
        assertFalse(hit1.isFilled("middle2"));
        toTest.markFilled(hit1);
        assertTrue(hit1.isFilled(PartialSummaryHandler.PRESENTATION));
        assertTrue(hit1.isFilled("middle2"));
    }

    @Test
    void testFieldsPresentation() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery(null);
        query.getPresentation().getSummaryFields().add("TOPIC");
        query.getPresentation().getSummaryFields().add("SCORE");
        query.getPresentation().getSummaryFields().add("CHUNK");
        query.getPresentation().getSummaryFields().add("BYTES");
        var hit1 = createHit(query);
        var hit2 = createHit(query, "default");
        var result = createResult(query, hit1, hit2);
        var toTest = new PartialSummaryHandler(set);
        toTest.wantToFill(result, PartialSummaryHandler.PRESENTATION);
        assertEquals("default", toTest.askForSummary());
        assertNotNull(toTest.askForFields());
        assertEquals(4, toTest.askForFields().size());
        assertFalse(toTest.resultAlreadyFilled());
        assertTrue(toTest.needFill(hit1));
        assertFalse(toTest.needFill(hit2));
        var docsumDef = toTest.effectiveDocsumDef();
        assertNotNull(docsumDef);
        assertEquals(4, docsumDef.fields().size());
        assertFalse(hit1.isFilled("[f:BYTES,CHUNK,SCORE,TOPIC]"));
        toTest.markFilled(hit1);
        assertTrue(hit1.isFilled(PartialSummaryHandler.PRESENTATION));
        assertTrue(hit1.isFilled("[f:BYTES,CHUNK,SCORE,TOPIC]"));
        assertFalse(hit1.isFilled(null));
        assertFalse(hit1.isFilled("default"));
        // no overlap
        assertEquals(0, result.hits().getFilled().size());
        // we will do this in dispatch code:
        toTest.markFilled(hit2);
        assertEquals(2, result.hits().getFilled().size());
    }

    @Test
    void testFieldsPlusClassPresentation() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        var query = createQuery("first3");
        query.getPresentation().getSummaryFields().add("TOPIC");
        query.getPresentation().getSummaryFields().add("SCORE");
        var hit1 = createHit(query);
        var hit2 = createHit(query, "default");
        var result = createResult(query, hit1, hit2);
        var toTest = new PartialSummaryHandler(set);
        toTest.wantToFill(result, PartialSummaryHandler.PRESENTATION);
        assertEquals("default", toTest.askForSummary());
        assertNotNull(toTest.askForFields());
        assertEquals(4, toTest.askForFields().size());
        assertFalse(toTest.resultAlreadyFilled());
        assertTrue(toTest.needFill(hit1));
        assertFalse(toTest.needFill(hit2));
        var docsumDef = toTest.effectiveDocsumDef();
        assertNotNull(docsumDef);
        assertEquals(4, docsumDef.fields().size());
        assertFalse(hit1.isFilled(PartialSummaryHandler.PRESENTATION));
        assertFalse(hit1.isFilled("[f:SCORE,TITLE,TOPIC,WORDS]"));
        toTest.markFilled(hit1);
        assertTrue(hit1.isFilled(PartialSummaryHandler.PRESENTATION));
        assertTrue(hit1.isFilled("[f:SCORE,TITLE,TOPIC,WORDS]"));
        assertFalse(hit1.isFilled(null));
        assertFalse(hit1.isFilled("default"));
        // no overlap
        assertEquals(0, result.hits().getFilled().size());
        // we will do this in dispatch code:
        toTest.markFilled(hit2);
        assertEquals(2, result.hits().getFilled().size());
    }

    @Test
    void enoughFields() {
        var query = createQuery("first3");
        query.getPresentation().setSummaryFields("field1");
        var hit1 = createHit(query);
        var result = createResult(query, hit1);
        assertEquals("[f:field1]", PartialSummaryHandler.enoughFields(null, result));
        assertEquals("[f:field1]", PartialSummaryHandler.enoughFields(PartialSummaryHandler.DEFAULT_CLASS, result));
        assertEquals("[f:field1]", PartialSummaryHandler.enoughFields(PartialSummaryHandler.PRESENTATION, result));
        assertEquals("default", PartialSummaryHandler.enoughFields("first3", result));
    }

    @Test
    void enoughFieldsWhenOnlyMatchFeaturesAreInSummaryFeatures() {
        var query = createQuery("first3");
        query.getPresentation().setSummaryFields("matchfeatures");
        var hit1 = createHit(query);
        var result = createResult(query, hit1);
        assertEquals("[f:matchfeatures]", PartialSummaryHandler.enoughFields(null, result));
        assertEquals("[f:matchfeatures]", PartialSummaryHandler.enoughFields(PartialSummaryHandler.DEFAULT_CLASS, result));
        assertEquals("[f:matchfeatures]", PartialSummaryHandler.enoughFields(PartialSummaryHandler.PRESENTATION, result));
        assertEquals("default", PartialSummaryHandler.enoughFields("first3", result));
    }

    static Query createQuery(String summaryClass) {
        if (summaryClass == null)
            return new Query("/search/?query=foo");
        else {
            return new Query("/search/?query=foo&presentation.summary=" + summaryClass);
        }
    }

    static Hit createHit(Query query, String... alreadyFilled) {
        var hit = new FastHit();
        hit.setQuery(query);
        hit.setFillable();
        for (String already : alreadyFilled) {
            hit.setFilled(already);
        }
        return hit;
    }

    static Result createResult(Query query, Hit... hits) {
        var result = new Result(query);
        for (Hit hit : hits) {
            result.hits().add(hit);
        }
        return result;
    }

    static DocsumDefinitionSet createDocsumDefinitionSet() {
        var schema = new Schema.Builder("test");
        var summary = new DocumentSummary.Builder("default");
        summary.add(new DocumentSummary.Field("TOPIC", "string"));
        summary.add(new DocumentSummary.Field("TITLE", "longstring"));
        summary.add(new DocumentSummary.Field("WORDS", "string"));
        summary.add(new DocumentSummary.Field("CHUNK", "string"));
        summary.add(new DocumentSummary.Field("SCORE", "integer"));
        summary.add(new DocumentSummary.Field("BYTES", "byte"));
        schema.add(summary.build());
        summary = new DocumentSummary.Builder("first3");
        summary.add(new DocumentSummary.Field("TOPIC", "string"));
        summary.add(new DocumentSummary.Field("TITLE", "longstring"));
        summary.add(new DocumentSummary.Field("WORDS", "string"));
        schema.add(summary.build());
        summary = new DocumentSummary.Builder("middle2");
        summary.add(new DocumentSummary.Field("WORDS", "string"));
        summary.add(new DocumentSummary.Field("CHUNK", "string"));
        schema.add(summary.build());
        summary = new DocumentSummary.Builder("last3");
        summary.add(new DocumentSummary.Field("CHUNK", "string"));
        summary.add(new DocumentSummary.Field("SCORE", "integer"));
        summary.add(new DocumentSummary.Field("BYTES", "byte"));
        schema.add(summary.build());
        return new DocsumDefinitionSet(schema.build());
    }

}
