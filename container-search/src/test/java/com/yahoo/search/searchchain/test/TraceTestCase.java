// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Tests tracing scenarios where traces from multiple executions over the same query are involved.
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon Bratseth</a>
 */
public class TraceTestCase extends junit.framework.TestCase {

    public void testTracingOnCorrectAPIUseNonParallel() {
        assertTracing(true,false);
    }

    public void testTracingOnIncorrectAPIUseNonParallel() {
        assertTracing(false,false);
    }

    public void testTracingOnCorrectAPIUseParallel() {
        assertTracing(true, true);
    }

    public void testTracingOnIncorrectAPIUseParallel() {
        assertTracing(false,true);
    }

    @SuppressWarnings("deprecation")
    public void assertTracing(boolean carryOverContext,boolean parallel) {
        Query query=new Query("?tracelevel=1");
        query.trace("Before execution",1);
        Chain<Searcher> forkingChain=new Chain<>(new Tracer("forker"),new Forker(carryOverContext,parallel,new Tracer("branch 1"),new Tracer("branch 2")));
        new Execution(forkingChain, Execution.Context.createContextStub()).search(query);

        // printTrace(query);

        if (carryOverContext)
            assertTraceWithChildExecutionMessages(query);
        else if (parallel)
            assertTrace(query);
        else
            assertIncorrectlyNestedTrace(query);

        assertCorrectRendering(query);
    }

    // The valid and usual trace
    private void assertTraceWithChildExecutionMessages(Query query) {
        Iterator<String> trace=collectTrace(query).iterator();
        assertEquals("(level start)",trace.next());
        assertEquals("  No query profile is used",trace.next());
        assertEquals("  Before execution",trace.next());
        assertEquals("  (level start)",trace.next());
        assertEquals("    During forker: 0",trace.next());
        assertEquals("    (level start)",trace.next());
        assertEquals("      During branch 1: 0",trace.next());
        assertEquals("    (level end)",trace.next());
        assertEquals("    (level start)",trace.next());
        assertEquals("      During branch 2: 0", trace.next());
        assertEquals("    (level end)",trace.next());
        assertEquals("  (level end)",trace.next());
        assertEquals("(level end)",trace.next());
        assertFalse(trace.hasNext());
    }

    // With incorrect API usage and query cloning (in parallel use) we get a valid trace
    // where the message of the execution subtrees is empty rather than "child execution". This is fine.
    private void assertTrace(Query query) {
        Iterator<String> trace=collectTrace(query).iterator();
        assertEquals("(level start)",trace.next());
        assertEquals("  No query profile is used",trace.next());
        assertEquals("  Before execution",trace.next());
        assertEquals("  (level start)",trace.next());
        assertEquals("    During forker: 0",trace.next());
        assertEquals("    (level start)",trace.next());
        assertEquals("      During branch 1: 0",trace.next());
        assertEquals("    (level end)",trace.next());
        assertEquals("    (level start)",trace.next());
        assertEquals("      During branch 2: 0", trace.next());
        assertEquals("    (level end)",trace.next());
        assertEquals("  (level end)",trace.next());
        assertEquals("(level end)",trace.next());
        assertFalse(trace.hasNext());
    }

    // With incorrect usage and no query cloning the trace nesting becomes incorrect
    // but all the trace messages are present.
    private void assertIncorrectlyNestedTrace(Query query) {
        Iterator<String> trace=collectTrace(query).iterator();
        assertEquals("(level start)",trace.next());
        assertEquals("  No query profile is used",trace.next());
        assertEquals("  Before execution",trace.next());
        assertEquals("  (level start)",trace.next());
        assertEquals("    During forker: 0",trace.next());
        assertEquals("    (level start)",trace.next());
        assertEquals("      During branch 1: 0",trace.next());
        assertEquals("      (level start)",trace.next());
        assertEquals("        During branch 2: 0", trace.next());
        assertEquals("      (level end)",trace.next());
        assertEquals("    (level end)",trace.next());
        assertEquals("  (level end)",trace.next());
        assertEquals("(level end)",trace.next());
        assertFalse(trace.hasNext());
    }

    private void assertCorrectRendering(Query query) {
        try {
            StringWriter writer=new StringWriter();
            query.getContext(false).render(writer);
            String expected=
                    "<meta type=\"context\">\n" +
                    "\n" +
                    "  <p>No query profile is used</p>\n" +
                    "\n" +
                    "  <p>Before execution</p>\n" +
                    "\n" +
                    "  <p>\n" +
                    "    <p>During forker: 0";
            assertEquals(expected,writer.toString().substring(0,expected.length()));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> collectTrace(Query query) {
        TraceCollector collector=new TraceCollector();
        query.getContext(false).getTrace().accept(collector);
        return collector.trace();
    }

    private static class TraceCollector extends TraceVisitor {

        private List<String> trace=new ArrayList<>();
        private StringBuilder indent=new StringBuilder();

        @Override
        public void entering(TraceNode node) {
            trace.add(indent + "(level start)");
            indent.append("  ");
        }

        @Override
        public void leaving(TraceNode end) {
            indent.setLength(indent.length()-2);
            trace.add(indent + "(level end)");
        }

        @Override
        public void visit(TraceNode node) {
            if (node.isRoot()) return;
            if (node.payload()==null) return;
            trace.add(indent + node.payload().toString());
        }

        public List<String> trace() { return trace; }
    }

    private static class Tracer extends Searcher {

        private String name;
        private int counter=0;

        public Tracer(String name) {
            this.name=name;
        }

        @Override
        public Result search(Query query, Execution execution) {
            query.trace("During " + name + ": " + (counter++) ,1);
            return execution.search(query);
        }
    }

    private static class Forker extends Searcher {

        private List<Searcher> branches;

        /** If true, this is using the api as recommended, if false, it is not */
        private boolean carryOverContext;

        /** If true, simulate parallel execution by cloning the query */
        private boolean parallel;

        public Forker(boolean carryOverContext,boolean parallel,Searcher ... branches) {
            this.carryOverContext=carryOverContext;
            this.parallel=parallel;
            this.branches=Arrays.asList(branches);
        }

        @SuppressWarnings("deprecation")
        @Override
        public Result search(Query query, Execution execution) {
            Result result=execution.search(query);
            for (Searcher branch : branches) {
                Query branchQuery=parallel ? query.clone() : query;
                Result branchResult=
                        ( carryOverContext ? new Execution(branch,execution.context()) : new Execution(branch, Execution.Context.createContextStub())).search(branchQuery);
                result.hits().add(branchResult.hits());
                result.mergeWith(branchResult);
            }
            return result;
        }

    }

}
