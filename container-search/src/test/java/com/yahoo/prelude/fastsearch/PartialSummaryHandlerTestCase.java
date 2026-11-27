// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.Schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(result.hits().isFilled(null));
        assertFalse(result.hits().isFilled("foobar"));
        hit2.setUnfillable();
        assertTrue(result.hits().isFilled("default"));
        assertTrue(result.hits().isFilled(null));
        assertFalse(result.hits().isFilled("foobar"));
        hit1.setUnfillable();
        assertTrue(result.hits().isFilled("foobar"));
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

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "default", "[presentation]" })
    void testOverlappingSummaryFetchesOnlyMissingFields(String summaryClass) {
        var query = createQuery(null);
        var hit = createHit(query, "first3");
        var result = createResult(query, hit);
        var handler = new PartialSummaryHandler(createDocsumDefinitionSet());
        handler.wantToFill(result, summaryClass);

        assertEquals("default", handler.askForSummary());
        assertEquals(Set.of("CHUNK", "SCORE", "BYTES"), handler.askForFields());
        assertEquals(handler.askForFields(), handler.effectiveDocsumDef().fields().keySet());
        assertFalse(handler.resultAlreadyFilled());
        assertTrue(handler.needFill(hit));
        // Fill checks must still cover the whole request, not just the fields fetched this time.
        assertTrue(handler.needFill(createHit(query, "last3")));

        handler.markFilled(hit);
        assertTrue(hit.isFilled(summaryClass));
        assertFalse(handler.needFill(hit));
        var nextFill = new PartialSummaryHandler(createDocsumDefinitionSet());
        nextFill.wantToFill(result, "default");
        assertTrue(nextFill.resultAlreadyFilled());
    }

    @Test
    void testNamedPresentationSummaryFetchesOnlyMissingFields() {
        var query = createQuery("middle2");
        var hit = createHit(query, "first3");
        var handler = new PartialSummaryHandler(createDocsumDefinitionSet());
        handler.wantToFill(createResult(query, hit), PartialSummaryHandler.PRESENTATION);

        assertEquals("middle2", handler.askForSummary());
        assertEquals(Set.of("CHUNK"), handler.askForFields());
        handler.markFilled(hit);
        assertTrue(hit.isFilled("middle2"));
        assertTrue(hit.isFilled(PartialSummaryHandler.PRESENTATION));
    }

    @ParameterizedTest
    @ValueSource(strings = { "[presentation]", "[f:CHUNK,WORDS]" })
    void testExplicitFieldsFetchOnlyMissingFields(String summaryClass) {
        var query = createQuery(null);
        query.getPresentation().setSummaryFields("WORDS,CHUNK");
        var hit = createHit(query, "first3");
        var handler = new PartialSummaryHandler(createDocsumDefinitionSet());
        handler.wantToFill(createResult(query, hit), summaryClass);

        assertEquals(Set.of("CHUNK"), handler.askForFields());
        assertEquals(Set.of("CHUNK"), handler.effectiveDocsumDef().fields().keySet());
        assertEquals(Set.of("WORDS", "CHUNK"), query.getPresentation().getSummaryFields());
        handler.markFilled(hit);
        assertTrue(hit.isFilled("[f:CHUNK,WORDS]"));
        assertFalse(hit.isFilled("default"));
        assertFalse(handler.needFill(hit));
    }

    @Test
    void testFieldsAreOmittedOnlyWhenFilledOnEveryHit() {
        var query = createQuery(null);
        var first = createHit(query, "first3");
        var second = createHit(query);
        var nested = new HitGroup("nested");
        nested.add(second);
        var result = createResult(query, first, nested, new Hit("unfillable"));
        var handler = new PartialSummaryHandler(createDocsumDefinitionSet());
        handler.wantToFill(result, "middle2");

        assertNull(handler.askForFields());
        assertTrue(handler.needFill(first));
        assertTrue(handler.needFill(second));

        second.setFilled("first3");
        handler = new PartialSummaryHandler(createDocsumDefinitionSet());
        handler.wantToFill(result, "middle2");
        assertEquals(Set.of("CHUNK"), handler.askForFields());
    }

    @Test
    void testOverlappingFieldsFromMultiplePreviousFills() {
        var query = createQuery(null);
        var hit = createHit(query, "first3", "[f:CHUNK]");
        var handler = new PartialSummaryHandler(createDocsumDefinitionSet());
        handler.wantToFill(createResult(query, hit), "default");

        assertEquals(Set.of("SCORE", "BYTES"), handler.askForFields());
        assertTrue(handler.needFill(hit));
    }

    @Test
    void testConflictingSummaryAndFieldsOmitPreviousFieldsAfterResolvingSummary() {
        var query = createQuery("first3");
        query.getPresentation().setSummaryFields("TOPIC,SCORE");
        var hit = createHit(query, "[f:TOPIC]");
        var handler = new PartialSummaryHandler(createDocsumDefinitionSet());
        handler.wantToFill(createResult(query, hit), PartialSummaryHandler.PRESENTATION);

        assertEquals("[all]", handler.askForSummary());
        assertEquals(Set.of("TITLE", "WORDS", "SCORE"), handler.askForFields());
        handler.markFilled(hit);
        assertTrue(hit.isFilled("[f:SCORE,TITLE,TOPIC,WORDS]"));
        assertFalse(hit.isFilled("[all]"));
    }

    @Test
    void testPreviouslyFilledFieldsOutsideSummaryDoNotRequireAnotherSummary() {
        var query = createQuery("middle2");
        query.getPresentation().setSummaryFields("TOPIC,CHUNK");
        var hit = createHit(query, "first3");
        var handler = new PartialSummaryHandler(createDocsumDefinitionSet());
        handler.wantToFill(createResult(query, hit), PartialSummaryHandler.PRESENTATION);

        assertEquals("middle2", handler.askForSummary());
        assertEquals(Set.of("CHUNK"), handler.askForFields());
        handler.markFilled(hit);
        assertTrue(hit.isFilled("[f:CHUNK,TOPIC]"));
    }

    @Test
    void testStreamingHasNoPreviousFillToOmit() {
        var query = createQuery("middle2");
        query.getPresentation().setSummaryFields("WORDS,CHUNK");
        var handler = new PartialSummaryHandler(createDocsumDefinitionSet());
        handler.wantToFill(query);

        assertEquals("middle2", handler.askForSummary());
        assertEquals(Set.of("WORDS", "CHUNK"), handler.askForFields());
        assertEquals(handler.askForFields(), handler.effectiveDocsumDef().fields().keySet());
    }

    @Test
    void testNoFillableHits() {
        var query = createQuery(null);
        var handler = new PartialSummaryHandler(createDocsumDefinitionSet());
        handler.wantToFill(createResult(query, new Hit("unfillable")), "default");
        assertTrue(handler.resultAlreadyFilled());
        assertNull(handler.askForFields());
    }

    static class TraceToString extends com.yahoo.yolean.trace.TraceVisitor {
        public StringBuilder target = new StringBuilder();
        public void visit(com.yahoo.yolean.trace.TraceNode node) {
            if (node.payload() != null) {
                target.append("\n\t>>> ");
                target.append(node.payload());
                target.append(" <<<");
            }
        }
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
        // so we have:
        // summary=first3 (TOPIC, TITLE, WORDS)
        // but fields = [ TOPIC, SCORE ]
        // so what do we expect to get?
        var tracer = new TraceToString();
        query.getModel().getExecution().trace().accept(tracer);
        assertTrue(tracer.target.toString()
                   .contains("requested summary=first3 does not contain all fields [SCORE, TOPIC] from query; trying fields=[SCORE, TITLE, TOPIC, WORDS] and summary=[all]"),
                   "Trace did not contain expected message, was: " + tracer.target);
        assertEquals("[all]", toTest.askForSummary());
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
        var hit1 = createHit(query);
        var result = createResult(query, hit1);
        assertEquals("first3", PartialSummaryHandler.enoughFields(null, result));
        assertEquals("first3", PartialSummaryHandler.enoughFields(PartialSummaryHandler.DEFAULT_CLASS, result));
        assertEquals("first3", PartialSummaryHandler.enoughFields(PartialSummaryHandler.PRESENTATION, result));
        assertEquals("[all]", PartialSummaryHandler.enoughFields("first3", result));
        query.getPresentation().setSummaryFields("field1");
        assertEquals("[f:field1]", PartialSummaryHandler.enoughFields(null, result));
        assertEquals("[f:field1]", PartialSummaryHandler.enoughFields(PartialSummaryHandler.DEFAULT_CLASS, result));
        assertEquals("[f:field1]", PartialSummaryHandler.enoughFields(PartialSummaryHandler.PRESENTATION, result));
        assertEquals("[all]", PartialSummaryHandler.enoughFields("first3", result));
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
        assertEquals("[all]", PartialSummaryHandler.enoughFields("first3", result));
    }

    static Query createQuery(String summaryClass) {
        String q = "/search/?query=foo&trace.level=3";
        if (summaryClass == null)
            return new Query(q);
        else {
            return new Query(q + "&presentation.summary=" + summaryClass);
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
