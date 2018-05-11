// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.ListenableFuture;
import com.yahoo.component.chain.Chain;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.grouping.result.DoubleBucketId;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.grouping.result.StringId;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.NanNumber;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.result.StructuredData;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.statistics.ElapsedTimeTestCase;
import com.yahoo.search.statistics.ElapsedTimeTestCase.CreativeTimeSource;
import com.yahoo.search.statistics.ElapsedTimeTestCase.UselessSearcher;
import com.yahoo.search.statistics.TimeTracker;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Utf8;
import com.yahoo.yolean.trace.TraceNode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;

/**
 * Functional testing of {@link JsonRenderer}.
 *
 * @author Steinar Knutsen
 */
public class JsonRendererTestCase {

    JsonRenderer originalRenderer;
    JsonRenderer renderer;

    public JsonRendererTestCase() {
        originalRenderer = new JsonRenderer();
    }

    @Before
    public void setUp() throws Exception {
        // Do the same dance as in production
        renderer = (JsonRenderer) originalRenderer.clone();
        renderer.init();
    }

    @After
    public void tearDown() throws Exception {
        renderer = null;
    }

    private static final class Thingie {
        @Override
        public String toString() {
            return "thingie";
        }
    }

    @Test
    public void testDocumentId() throws IOException, InterruptedException, ExecutionException, JSONException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"fields\": {\n"
                + "                    \"documentid\": \"id:unittest:smoke::whee\"\n"
                + "                },\n"
                + "                \"id\": \"id:unittest:smoke::whee\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = newEmptyResult();
        Hit h = new Hit("docIdTest");
        h.setField("documentid", new DocumentId("id:unittest:smoke::whee"));
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    private Result newEmptyResult(String[] args) {
        return new Result(new Query("/?" + String.join("&", args)));
    }

    private Result newEmptyResult() {
        return newEmptyResult(new String[] {"query=a"});
    }

    @Test
    public void testDataTypes() throws IOException, InterruptedException, ExecutionException, JSONException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"fields\": {\n"
                + "                    \"double\": 0.00390625,\n"
                + "                    \"float\": 14.29,\n"
                + "                    \"integer\": 1,\n"
                + "                    \"long\": 4398046511104,\n"
                + "                    \"object\": \"thingie\",\n"
                + "                    \"string\": \"stuff\",\n"
                + "                    \"predicate\": \"a in [b]\",\n"
                + "                    \"tensor1\": { \"cells\": [ { \"address\": {\"x\": \"a\"}, \"value\":2.0 } ] },\n"
                + "                    \"tensor2\": { \"cells\": [] },\n"
                + "                    \"tensor3\": { \"cells\": [ { \"address\": {\"x\": \"a\", \"y\": \"0\"}, \"value\":2.0 }, { \"address\": {\"x\": \"a\", \"y\": \"1\"}, \"value\":-1.0 } ] }\n"
                + "                },\n"
                + "                \"id\": \"datatypestuff\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = newEmptyResult();
        Hit h = new Hit("datatypestuff");
        // the floating point values are chosen to get a deterministic string representation
        h.setField("double", 0.00390625d);
        h.setField("float", 14.29f);
        h.setField("integer", 1);
        h.setField("long", 4398046511104L);
        h.setField("string", "stuff");
        h.setField("predicate", Predicate.fromString("a in [b]"));
        h.setField("tensor1", new TensorFieldValue(Tensor.from("{ {x:a}: 2.0}")));
        h.setField("tensor2", new TensorFieldValue(TensorType.empty));
        h.setField("tensor3", Tensor.from("{ {x:a, y:0}: 2.0, {x:a, y:1}: -1 }"));
        h.setField("object", new Thingie());
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }


    @Test
    public final void testTracing() throws IOException, InterruptedException, ExecutionException {
        // which clearly shows a trace child is created once too often...
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 0\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    },\n"
                + "    \"trace\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"message\": \"No query profile is used\"\n"
                + "            },\n"
                + "            {\n"
                + "                \"children\": [\n"
                + "                    {\n"
                + "                        \"message\": \"something\"\n"
                + "                    },\n"
                + "                    {\n"
                + "                        \"message\": \"something else\"\n"
                + "                    },\n"
                + "                    {\n"
                + "                        \"children\": [\n"
                + "                            {\n"
                + "                                \"message\": \"yellow\"\n"
                + "                            }\n"
                + "                        ]\n"
                + "                    },\n"
                + "                    {\n"
                + "                        \"message\": \"marker\"\n"
                + "                    }\n"
                + "                ]\n"
                + "            }\n"
                + "        ]\n"
                + "    }\n"
                + "}\n";
        Query q = new Query("/?query=a&tracelevel=1");
        Execution execution = new Execution(Execution.Context.createContextStub());
        Result r = new Result(q);

        execution.search(q);
        q.trace("something", 1);
        q.trace("something else", 1);
        Execution e2 = new Execution(new Chain<Searcher>(), execution.context());
        Query subQuery = new Query("/?query=b&tracelevel=1");
        e2.search(subQuery);
        subQuery.trace("yellow", 1);
        q.trace("marker", 1);
        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }

    @Test
    public final void testEmptyTracing() throws IOException, InterruptedException, ExecutionException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 0\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Query q = new Query("/?query=a&tracelevel=0");
        Execution execution = new Execution(Execution.Context.createContextStub());
        Result r = new Result(q);

        execution.search(q);
        Execution e2 = new Execution(new Chain<Searcher>(), execution.context());
        Query subQuery = new Query("/?query=b&tracelevel=0");
        e2.search(subQuery);
        subQuery.trace("yellow", 1);
        q.trace("marker", 1);
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        ListenableFuture<Boolean> f = renderer.render(bs, r, execution, null);
        assertTrue(f.get());
        String summary = Utf8.toString(bs.toByteArray());
        assertEqualJson(expected, summary);
    }

    @SuppressWarnings("unchecked")
    @Test
    public final void testTracingWithEmptySubtree() throws IOException, InterruptedException, ExecutionException {
        String expected =  "{\n"
                + "    \"root\": {\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 0\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    },\n"
                + "    \"trace\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"message\": \"No query profile is used\"\n"
                + "            },\n"
                + "            {\n"
                + "                \"message\": \"Resolved properties:\\ntracelevel=10 (value from request)\\nquery=a (value from request)\\n\"\n"
                + "            },\n"
                + "            {\n"
                + "                \"children\": [\n"
                + "                    {\n"
                + "                        \"timestamp\": 42\n"
                + "                    }\n"
                + "                ]\n"
                + "            }\n"
                + "        ]\n"
                + "    }\n"
                + "}";
        Query q = new Query("/?query=a&tracelevel=10");
        Execution execution = new Execution(Execution.Context.createContextStub());
        Result r = new Result(q);

        execution.search(q);
        new Execution(new Chain<>(), execution.context());
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        ListenableFuture<Boolean> f = renderer.render(bs, r, execution, null);
        assertTrue(f.get());
        String summary = Utf8.toString(bs.toByteArray());
        ObjectMapper m = new ObjectMapper();

        Map<String, Object> exp = m.readValue(expected, Map.class);
        Map<String, Object> gen = m.readValue(summary, Map.class);
        {
            // nuke timestamp and check it's there
            Map<String, Object> trace = (Map<String, Object>) gen.get("trace");
            List<Object> children1 = (List<Object>) trace.get("children");
            Map<String, Object> subtrace = (Map<String, Object>) children1.get(2);
            List<Object> children2 = (List<Object>) subtrace.get("children");
            Map<String, Object> traceElement = (Map<String, Object>) children2.get(0);
            traceElement.put("timestamp", Integer.valueOf(42));
        }
        assertEquals(exp, gen);
    }

    @Test
    public void trace_is_not_included_if_tracelevel_0() throws IOException, ExecutionException, InterruptedException {
        String expected =
                "{\n" +
                "  \"root\": {\n" +
                "    \"id\": \"toplevel\",\n" +
                "    \"relevance\": 1.0,\n" +
                "    \"fields\": {\n" +
                "      \"totalCount\": 0\n" +
                "    }\n" +
                "  }\n" +
                "}";
        Query q = new Query("/?query=a&tracelevel=0");
        Execution execution = new Execution(Execution.Context.createContextStub());
        Result r = new Result(q);
        execution.search(q);
        q.getContext(true).setProperty("prop-key", "prop-value");
        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }

    private void subExecution(Execution execution, String color, int traceLevel) {
        Execution e2 = new Execution(new Chain<Searcher>(), execution.context());
        Query subQuery = new Query("/?query=b&tracelevel=" + traceLevel);
        e2.search(subQuery);
        subQuery.trace(color, 1);
    }

    @Test
    public final void testTracingOfNodesWithBothChildrenAndData() throws IOException, InterruptedException, ExecutionException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 0\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    },\n"
                + "    \"trace\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"message\": \"No query profile is used\"\n"
                + "            },\n"
                + "            {\n"
                + "                \"children\": [\n"
                + "                    {\n"
                + "                        \"message\": \"string payload\",\n"
                + "                        \"children\": ["
                + "                            {\n"
                + "                                \"message\": \"leafnode\""
                + "                            }\n"
                + "                        ]\n"
                + "                    },\n"
                + "                    {\n"
                + "                        \"message\": \"something\"\n"
                + "                    }\n"
                + "                ]\n"
                + "            }\n"
                + "        ]\n"
                + "    }\n"
                + "}\n";
        Query q = new Query("/?query=a&tracelevel=1");
        Execution execution = new Execution(Execution.Context.createContextStub());
        Result r = new Result(q);
        execution.search(q);
        final TraceNode child = new TraceNode("string payload", 0L);
        child.add(new TraceNode("leafnode", 0L));
        execution.trace().traceNode().add(child);
        q.trace("something", 1);
        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }


    @Test
    public final void testTracingOfNodesWithBothChildrenAndDataAndEmptySubnode() throws IOException, InterruptedException, ExecutionException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 0\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    },\n"
                + "    \"trace\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"message\": \"No query profile is used\"\n"
                + "            },\n"
                + "            {\n"
                + "                \"children\": [\n"
                + "                    {\n"
                + "                        \"message\": \"string payload\"\n"
                + "                    },\n"
                + "                    {\n"
                + "                        \"message\": \"something\"\n"
                + "                    }\n"
                + "                ]\n"
                + "            }\n"
                + "        ]\n"
                + "    }\n"
                + "}\n";
        Query q = new Query("/?query=a&tracelevel=1");
        Execution execution = new Execution(
                Execution.Context.createContextStub());
        Result r = new Result(q);
        execution.search(q);
        final TraceNode child = new TraceNode("string payload", 0L);
        child.add(new TraceNode(null, 0L));
        execution.trace().traceNode().add(child);
        q.trace("something", 1);
        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }

    @Test
    public final void testTracingOfNestedNodesWithDataAndSubnodes() throws IOException, InterruptedException, ExecutionException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 0\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    },\n"
                + "    \"trace\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"message\": \"No query profile is used\"\n"
                + "            },\n"
                + "            {\n"
                + "                \"children\": [\n"
                + "                    {\n"
                + "                        \"message\": \"string payload\",\n"
                + "                        \"children\": [\n"
                + "                            {\n"
                + "                                \"children\": [\n"
                + "                                    {\n"
                + "                                        \"message\": \"in OO languages, nesting is for birds\"\n"
                + "                                    }\n"
                + "                                ]\n"
                + "                            }\n"
                + "                        ]\n"
                + "                    }\n"
                + "                ]\n"
                + "            }\n"
                + "        ]\n"
                + "    }\n"
                + "}\n";
        Query q = new Query("/?query=a&tracelevel=1");
        Execution execution = new Execution(
                Execution.Context.createContextStub());
        Result r = new Result(q);
        execution.search(q);
        final TraceNode child = new TraceNode("string payload", 0L);
        final TraceNode childOfChild = new TraceNode(null, 0L);
        child.add(childOfChild);
        childOfChild.add(new TraceNode("in OO languages, nesting is for birds", 0L));
        execution.trace().traceNode().add(child);
        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }


    @Test
    public final void test() throws IOException, InterruptedException, ExecutionException, JSONException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"children\": [\n"
                + "                    {\n"
                + "                        \"fields\": {\n"
                + "                            \"c\": \"d\",\n"
                + "                            \"uri\": \"http://localhost/1\"\n"
                + "                        },\n"
                + "                        \"id\": \"http://localhost/1\",\n"
                + "                        \"relevance\": 0.9,\n"
                + "                        \"types\": [\n"
                + "                            \"summary\"\n"
                + "                        ]\n"
                + "                    }\n"
                + "                ],\n"
                + "                \"id\": \"usual\",\n"
                + "                \"relevance\": 1.0\n"
                + "            },\n"
                + "            {\n"
                + "                \"fields\": {\n"
                + "                    \"e\": \"f\"\n"
                + "                },\n"
                + "                \"id\": \"type grouphit\",\n"
                + "                \"relevance\": 1.0,\n"
                + "                \"types\": [\n"
                + "                    \"grouphit\"\n"
                + "                ]\n"
                + "            },\n"
                + "            {\n"
                + "                \"fields\": {\n"
                + "                    \"b\": \"foo\",\n"
                + "                    \"uri\": \"http://localhost/\"\n"
                + "                },\n"
                + "                \"id\": \"http://localhost/\",\n"
                + "                \"relevance\": 0.95,\n"
                + "                \"types\": [\n"
                + "                    \"summary\"\n"
                + "                ]\n"
                + "            }\n"
                + "        ],\n"
                + "        \"coverage\": {\n"
                + "            \"coverage\": 100,\n"
                + "            \"documents\": 500,\n"
                + "            \"full\": true,\n"
                + "            \"nodes\": 1,\n"
                + "            \"results\": 1,\n"
                + "            \"resultsFull\": 1\n"
                + "        },\n"
                + "        \"errors\": [\n"
                + "            {\n"
                + "                \"code\": 18,\n"
                + "                \"message\": \"boom\",\n"
                + "                \"summary\": \"Internal server error.\"\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 0\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}";
        Query q = new Query("/?query=a&tracelevel=5&reportCoverage=true");
        Execution execution = new Execution(
                Execution.Context.createContextStub());
        Result r = new Result(q);
        r.setCoverage(new Coverage(500, 1, true));

        FastHit h = new FastHit("http://localhost/", .95);
        h.setField("$a", "Hello, world.");
        h.setField("b", "foo");
        r.hits().add(h);
        HitGroup g = new HitGroup("usual");
        h = new FastHit("http://localhost/1", .90);
        h.setField("c", "d");
        g.add(h);
        r.hits().add(g);
        HitGroup gg = new HitGroup("type grouphit");
        gg.types().add("grouphit");
        gg.setField("e", "f");
        r.hits().add(gg);
        r.hits().addError(ErrorMessage.createInternalServerError("boom"));
        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testCoverage() throws InterruptedException, ExecutionException, IOException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"coverage\": {\n"
                + "            \"coverage\": 83,\n"
                + "            \"documents\": 500,\n"
                + "            \"degraded\" : {\n"
                + "                \"match-phase\" : true,\n"
                + "                \"timeout\" : false,\n"
                + "                \"adaptive-timeout\" : true,\n"
                + "                \"non-ideal-state\" : false"
                + "            },\n"
                + "            \"full\": false,\n"
                + "            \"nodes\": 0,\n"
                + "            \"results\": 1,\n"
                + "            \"resultsFull\": 0\n"
                + "        },\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 0\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}";
        Query q = new Query("/?query=a&tracelevel=5&reportCoverage=true");
        Execution execution = new Execution(Execution.Context.createContextStub());
        Result r = new Result(q);
        r.setCoverage(new Coverage(500, 600).setDegradedReason(5));

        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testMoreTypes() throws InterruptedException, ExecutionException, IOException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"fields\": {\n"
                + "                    \"bigDecimal\": 3.402823669209385e+38,\n"
                + "                    \"bigInteger\": 340282366920938463463374607431768211455,\n"
                + "                    \"byte\": 8,\n"
                + "                    \"short\": 16\n"
                + "                },\n"
                + "                \"id\": \"moredatatypestuff\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = newEmptyResult();
        Hit h = new Hit("moredatatypestuff");
        h.setField("byte", Byte.valueOf((byte) 8));
        h.setField("short", Short.valueOf((short) 16));
        h.setField("bigInteger", new BigInteger(
                "340282366920938463463374607431768211455"));
        h.setField("bigDecimal", new BigDecimal(
                "340282366920938463463374607431768211456.5"));
        h.setField("nanNumber", NanNumber.NaN);
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testNullField() throws InterruptedException, ExecutionException, IOException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"fields\": {\n"
                + "                    \"null\": null\n"
                + "                },\n"
                + "                \"id\": \"nullstuff\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = newEmptyResult();
        Hit h = new Hit("nullstuff");
        h.setField("null", null);
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testHitWithSource() throws IOException, InterruptedException, ExecutionException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"id\": \"datatypestuff\",\n"
                + "                \"relevance\": 1.0,\n"
                + "                \"source\": \"unit test\"\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = newEmptyResult();
        Hit h = new Hit("datatypestuff");
        h.setSource("unit test");
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testErrorWithStackTrace() throws InterruptedException, ExecutionException, IOException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"errors\": [\n"
                + "            {\n"
                + "                \"code\": 1234,\n"
                + "                \"message\": \"top of the day\",\n"
                + "                \"stackTrace\": \"java.lang.Throwable\\n\\tat com.yahoo.search.rendering.JsonRendererTestCase.testErrorWithStackTrace(JsonRendererTestCase.java:732)\\n\",\n"
                + "                \"summary\": \"hello\"\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 0\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Query q = new Query("/?query=a&tracelevel=5&reportCoverage=true");
        Result r = new Result(q);
        Throwable t = new Throwable();
        StackTraceElement[] stack = new StackTraceElement[1];
        stack[0] = new StackTraceElement(
                "com.yahoo.search.rendering.JsonRendererTestCase",
                "testErrorWithStackTrace", "JsonRendererTestCase.java", 732);
        t.setStackTrace(stack);
        ErrorMessage e = new ErrorMessage(1234, "hello", "top of the day", t);
        r.hits().addError(e);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testContentHeader() {
        assertEquals("utf-8", renderer.getEncoding());
        assertEquals("application/json", renderer.getMimeType());
    }

    @Test
    public void testGrouping() throws InterruptedException, ExecutionException, IOException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"children\": [\n"
                + "                    {\n"
                + "                        \"children\": [\n"
                + "                            {\n"
                + "                                \"fields\": {\n"
                + "                                    \"count()\": 7\n"
                + "                                },\n"
                + "                                \"value\": \"Jones\",\n"
                + "                                \"id\": \"group:string:Jones\",\n"
                + "                                \"relevance\": 1.0\n"
                + "                            }\n"
                + "                        ],\n"
                + "                        \"continuation\": {\n"
                + "                            \"next\": \"CCCC\",\n"
                + "                            \"prev\": \"BBBB\"\n"
                + "                        },\n"
                + "                        \"id\": \"grouplist:customer\",\n"
                + "                        \"label\": \"customer\",\n"
                + "                        \"relevance\": 1.0\n"
                + "                    }\n"
                + "                ],\n"
                + "                \"continuation\": {\n"
                + "                    \"this\": \"AAAA\"\n"
                + "                },\n"
                + "                \"id\": \"group:root:0\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = newEmptyResult();
        RootGroup rg = new RootGroup(0, new Continuation() {
            @Override
            public String toString() {
                return "AAAA";
            }
        });
        GroupList gl = new GroupList("customer");
        gl.continuations().put("prev", new Continuation() {
            @Override
            public String toString() {
                return "BBBB";
            }
        });
        gl.continuations().put("next", new Continuation() {
            @Override
            public String toString() {
                return "CCCC";
            }
        });
        Group g = new Group(new StringId("Jones"), new Relevance(1.0));
        g.setField("count()", Integer.valueOf(7));
        gl.add(g);
        rg.add(gl);
        r.hits().add(rg);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testGroupingWithBucket() throws InterruptedException, ExecutionException, IOException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"children\": [\n"
                + "                    {\n"
                + "                        \"children\": [\n"
                + "                            {\n"
                + "                                \"fields\": {\n"
                + "                                    \"something()\": 7\n"
                + "                                },\n"
                + "                                \"limits\": {\n"
                + "                                    \"from\": \"1.0\",\n"
                + "                                    \"to\": \"2.0\"\n"
                + "                                },\n"
                + "                                \"id\": \"group:double_bucket:1.0:2.0\",\n"
                + "                                \"relevance\": 1.0\n"
                + "                            }\n"
                + "                        ],\n"
                + "                        \"id\": \"grouplist:customer\",\n"
                + "                        \"label\": \"customer\",\n"
                + "                        \"relevance\": 1.0\n"
                + "                    }\n"
                + "                ],\n"
                + "                \"continuation\": {\n"
                + "                    \"this\": \"AAAA\"\n"
                + "                },\n"
                + "                \"id\": \"group:root:0\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = newEmptyResult();
        RootGroup rg = new RootGroup(0, new Continuation() {
            @Override
            public String toString() {
                return "AAAA";
            }
        });
        GroupList gl = new GroupList("customer");
        Group g = new Group(new DoubleBucketId(1.0, 2.0), new Relevance(1.0));
        g.setField("something()", Integer.valueOf(7));
        gl.add(g);
        rg.add(gl);
        r.hits().add(rg);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testJsonObjects() throws InterruptedException, ExecutionException, IOException, JSONException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"fields\": {\n"
                + "                    \"inspectable\": {\n"
                + "                        \"a\": \"b\"\n"
                + "                    },\n"
                + "                    \"jackson\": {\n"
                + "                        \"Nineteen-eighty-four\": 1984\n"
                + "                    },\n"
                + "                    \"json producer\": {\n"
                + "                        \"long in structured\": 7809531904\n"
                + "                    },\n"
                + "                    \"org.json array\": [\n"
                + "                        true,\n"
                + "                        true,\n"
                + "                        false\n"
                + "                    ],\n"
                + "                    \"org.json object\": {\n"
                + "                        \"forty-two\": 42\n"
                + "                    }\n"
                + "                },\n"
                + "                \"id\": \"json objects\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 0\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = newEmptyResult();
        Hit h = new Hit("json objects");
        JSONObject o = new JSONObject();
        JSONArray a = new JSONArray();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode j = mapper.createObjectNode();
        JSONString s = new JSONString("{\"a\": \"b\"}");
        Slime slime = new Slime();
        Cursor c = slime.setObject();
        c.setLong("long in structured", 7809531904L);
        SlimeAdapter slimeInit = new SlimeAdapter(slime.get());
        StructuredData struct = new StructuredData(slimeInit);
        ((ObjectNode) j).put("Nineteen-eighty-four", 1984);
        o.put("forty-two", 42);
        a.put(true);
        a.put(true);
        a.put(false);
        h.setField("inspectable", s);
        h.setField("jackson", j);
        h.setField("json producer", struct);
        h.setField("org.json array", a);
        h.setField("org.json object", o);
        r.hits().add(h);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public final void testFieldValueInHit() throws IOException, InterruptedException, ExecutionException, JSONException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"fields\": {\n"
                + "                    \"fromDocumentApi\":{\"integerField\":123, \"stringField\":\"abc\"}"
                + "                },\n"
                + "                \"id\": \"fieldValueTest\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = newEmptyResult();
        Hit h = new Hit("fieldValueTest");
        StructDataType structType = new StructDataType("jsonRenderer");
        structType.addField(new Field("stringField", DataType.STRING));
        structType.addField(new Field("integerField", DataType.INT));
        Struct struct = structType.createFieldValue();
        struct.setFieldValue("stringField", "abc");
        struct.setFieldValue("integerField", 123);
        h.setField("fromDocumentApi", struct);
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public final void testHiddenFields() throws IOException, InterruptedException, ExecutionException, JSONException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"id\": \"hiddenFields\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = newEmptyResult();
        Hit h = createHitWithOnlyHiddenFields();
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    private Hit createHitWithOnlyHiddenFields() {
        Hit h = new Hit("hiddenFields");
        h.setField("NaN", NanNumber.NaN);
        h.setField("emptyString", "");
        h.setField("emptyStringFieldValue", new StringFieldValue(""));
        h.setField("$vespaImplementationDetail", "Hello, World!");
        return h;
    }

    @Test
    public final void testDebugRendering() throws IOException, InterruptedException, ExecutionException, JSONException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"fields\": {\n"
                + "                    \"NaN\": \"NaN\",\n"
                + "                    \"emptyString\": \"\",\n"
                + "                    \"emptyStringFieldValue\": \"\",\n"
                + "                    \"$vespaImplementationDetail\": \"Hello, World!\"\n"
                + "                },\n"
                + "                \"id\": \"hiddenFields\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";
        Result r = new Result(new Query("/?renderer.json.debug=true"));
        Hit h = createHitWithOnlyHiddenFields();
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public final void testTimingRendering() throws InterruptedException, ExecutionException, JsonParseException, JsonMappingException, IOException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    },"
                + "    \"timing\": {"
                + "        \"querytime\": 0.006,"
                + "        \"searchtime\": 0.007,"
                + "        \"summaryfetchtime\": 0.0"
                + "    }"
                + "}";
        Result r = new Result(new Query("/?renderer.json.debug=true&presentation.timing=true"));
        TimeTracker t = new TimeTracker(new Chain<Searcher>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third")));
        ElapsedTimeTestCase.doInjectTimeSource(t, new CreativeTimeSource(
                new long[] { 1L, 2L, 3L, 4L, 5L, 6L, 7L }));
        t.sampleSearch(0, true);
        t.sampleSearch(1, true);
        t.sampleSearch(2, true);
        t.sampleSearch(3, true);
        t.sampleSearchReturn(2, true, null);
        t.sampleSearchReturn(1, true, null);
        t.sampleSearchReturn(0, true, null);
        r.getElapsedTime().add(t);
        renderer.setTimeSource(() -> 8L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public final void testJsonCallback() throws IOException, InterruptedException, ExecutionException, JSONException {
        String expected = "{\n"
                + "    \"root\": {\n"
                + "        \"children\": [\n"
                + "            {\n"
                + "                \"fields\": {\n"
                + "                    \"documentid\": \"id:unittest:smoke::whee\"\n"
                + "                },\n"
                + "                \"id\": \"id:unittest:smoke::whee\",\n"
                + "                \"relevance\": 1.0\n"
                + "            }\n"
                + "        ],\n"
                + "        \"fields\": {\n"
                + "            \"totalCount\": 1\n"
                + "        },\n"
                + "        \"id\": \"toplevel\",\n"
                + "        \"relevance\": 1.0\n"
                + "    }\n"
                + "}\n";

        String jsonCallback = "some_function_name";
        Result r = newEmptyResult(new String[] {"query=a", "jsoncallback="+jsonCallback} );
        Hit h = new Hit("jsonCallbackTest");
        h.setField("documentid", new DocumentId("id:unittest:smoke::whee"));
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);

        String jsonCallbackBegin = summary.substring(0, jsonCallback.length() + 1);
        String jsonCallbackEnd = summary.substring(summary.length() - 2);
        String json = summary.substring(jsonCallback.length() + 1, summary.length() - 2);

        assertEquals(jsonCallback + "(", jsonCallbackBegin);
        assertEqualJson(expected, json);
        assertEquals(");", jsonCallbackEnd);
    }

    @Test
    public void testThatTheJsonValidatorCanCatchErrors() {
        String json = "{"
                + "    \"root\": {"
                + "        \"duplicate\": 1,"
                + "        \"duplicate\": 2"
                + "    }"
                + "}";
        assertEquals("Duplicate key \"duplicate\"", validateJSON(json));
    }
    private String render(Result r) throws InterruptedException, ExecutionException {
        Execution execution = new Execution(Execution.Context.createContextStub());
        return render(execution, r);
    }

    private String render(Execution execution, Result r) throws InterruptedException, ExecutionException {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        ListenableFuture<Boolean> f = renderer.render(bs, r, execution, null);
        assertTrue(f.get());
        String summary = Utf8.toString(bs.toByteArray());
        return summary;
    }

    @SuppressWarnings("unchecked")
    private void assertEqualJson(String expected, String generated) throws IOException {
        ObjectMapper m = new ObjectMapper();
        Map<String, Object> exp = m.readValue(expected, Map.class);
        Map<String, Object> gen = m.readValue(generated, Map.class);
        assertEquals(exp, gen);
        assertEquals("", validateJSON(expected));
        assertEquals("", validateJSON(generated));
    }
    private String validateJSON(String presumablyValidJson) {
        try {
            new JSONObject(presumablyValidJson);
            return "";
        } catch (JSONException e) {
            return e.getMessage();
        }
    }

}
