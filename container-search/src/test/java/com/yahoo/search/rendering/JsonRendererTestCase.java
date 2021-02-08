// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.ListenableFuture;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.RawData;
import com.yahoo.prelude.searcher.JuniperSearcher;
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
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.NanNumber;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.result.StructuredData;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import com.yahoo.search.statistics.ElapsedTimeTestCase;
import com.yahoo.search.statistics.ElapsedTimeTestCase.CreativeTimeSource;
import com.yahoo.search.statistics.ElapsedTimeTestCase.UselessSearcher;
import com.yahoo.search.statistics.TimeTracker;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.text.Utf8;
import com.yahoo.yolean.trace.TraceNode;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Functional testing of {@link JsonRenderer}.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
public class JsonRendererTestCase {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private JsonRenderer originalRenderer;
    private JsonRenderer renderer;

    public JsonRendererTestCase() {
        originalRenderer = new JsonRenderer();
    }

    @Before
    public void setUp() throws Exception {
        // Use the shared renderer as a prototype object, as specified in the API contract
        renderer = (JsonRenderer) originalRenderer.clone();
        renderer.init();
    }

    @Test
    public void testDocumentId() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"fields\": {"
                + "                    \"documentid\": \"id:unittest:smoke::whee\""
                + "                },"
                + "                \"id\": \"id:unittest:smoke::whee\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Result r = newEmptyResult();
        Hit h = new Hit("docIdTest");
        h.setField("documentid", new DocumentId("id:unittest:smoke::whee"));
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testDataTypes() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"fields\": {"
                + "                    \"double\": 0.00390625,"
                + "                    \"float\": 14.29,"
                + "                    \"integer\": 1,"
                + "                    \"long\": 4398046511104,"
                + "                    \"bool\": true,"
                + "                    \"object\": \"thingie\","
                + "                    \"string\": \"stuff\","
                + "                    \"predicate\": \"a in [b]\","
                + "                    \"tensor1\": { \"cells\": [ { \"address\": {\"x\": \"a\"}, \"value\":2.0 } ] },"
                + "                    \"tensor2\": { \"cells\": [] },"
                + "                    \"tensor3\": { \"cells\": [ { \"address\": {\"x\": \"a\", \"y\": \"0\"}, \"value\":2.0 }, { \"address\": {\"x\": \"a\", \"y\": \"1\"}, \"value\":-1.0 } ] },"
                + "                    \"summaryfeatures\": {"
                + "                        \"scalar1\":1.5,"
                + "                        \"scalar2\":2.5,"
                + "                        \"tensor1\":{\"type\":\"tensor(x[3])\",\"cells\":[{\"address\":{\"x\":\"0\"},\"value\":1.5},{\"address\":{\"x\":\"1\"},\"value\":2.0},{\"address\":{\"x\":\"2\"},\"value\":2.5}]},"
                + "                        \"tensor2\":{\"type\":\"tensor()\",\"cells\":[{\"address\":{},\"value\":0.5}]}"
                + "                    },"
                + "                    \"data\": \"Data \\\\xc3\\\\xa6 \\\\xc3\\\\xa5\""
                + "                },"
                + "                \"id\": \"datatypestuff\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Result r = newEmptyResult();
        Hit h = new Hit("datatypestuff");
        // the floating point values are chosen to get a deterministic string representation
        h.setField("double", 0.00390625d);
        h.setField("float", 14.29f);
        h.setField("integer", 1);
        h.setField("long", 4398046511104L);
        h.setField("bool", true);
        h.setField("string", "stuff");
        h.setField("predicate", Predicate.fromString("a in [b]"));
        h.setField("tensor1", new TensorFieldValue(Tensor.from("{ {x:a}: 2.0}")));
        h.setField("tensor2", new TensorFieldValue(TensorType.empty));
        h.setField("tensor3", Tensor.from("{ {x:a, y:0}: 2.0, {x:a, y:1}: -1 }"));
        h.setField("object", new Thingie());
        h.setField("summaryfeatures", createSummaryFeatures());
        h.setField("data", new RawData("Data æ å".getBytes(StandardCharsets.UTF_8)));
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    private FeatureData createSummaryFeatures() {
        Slime slime = new Slime();
        Cursor features = slime.setObject();
        features.setDouble("scalar1", 1.5);
        features.setDouble("scalar2", 2.5);
        Tensor tensor1 = Tensor.from("tensor(x[3]):[1.5, 2, 2.5]");
        features.setData("tensor1", TypedBinaryFormat.encode(tensor1));
        Tensor tensor2 = Tensor.from(0.5);
        features.setData("tensor2", TypedBinaryFormat.encode(tensor2));
        return new FeatureData(new SlimeAdapter(slime.get()));
    }

    @Test
    public void testTracing() throws IOException, InterruptedException, ExecutionException {
        // which clearly shows a trace child is created once too often...
        String expected = "{"
                + "    \"root\": {"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    },"
                + "    \"trace\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"message\": \"No query profile is used\""
                + "            },"
                + "            {"
                + "                \"children\": ["
                + "                    {"
                + "                        \"message\": \"something\""
                + "                    },"
                + "                    {"
                + "                        \"message\": \"something else\""
                + "                    },"
                + "                    {"
                + "                        \"children\": ["
                + "                            {"
                + "                                \"message\": \"yellow\""
                + "                            }"
                + "                        ]"
                + "                    },"
                + "                    {"
                + "                        \"message\": \"marker\""
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "}";
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
    public void testTracingOfSlime() throws IOException, InterruptedException, ExecutionException {
        // which clearly shows a trace child is created once too often...
        String expected = "{"
                + "    \"root\": {"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    },"
                + "    \"trace\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"message\": \"No query profile is used\""
                + "            },"
                + "            {"
                + "                \"children\": ["
                + "                    {"
                + "                        \"message\": \"something\""
                + "                    },"
                + "                    {"
                + "                        \"message\": \"something else\""
                + "                    },"
                + "                    {"
                + "                        \"children\": ["
                + "                            {"
                + "                                \"message\": ["
                + "                                    { \"colour\": \"yellow\"},"
                + "                                    { \"colour\": \"green\"}"
                + "                                 ]"
                + "                            }"
                + "                        ]"
                + "                    },"
                + "                    {"
                + "                        \"message\": \"marker\""
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "}";
        Query q = new Query("/?query=a&tracelevel=1");
        Execution execution = new Execution(Execution.Context.createContextStub());
        Result r = new Result(q);

        execution.search(q);
        q.trace("something", 1);
        q.trace("something else", 1);
        Execution e2 = new Execution(new Chain<Searcher>(), execution.context());
        Query subQuery = new Query("/?query=b&tracelevel=1");
        e2.search(subQuery);
        Value.ArrayValue access = new Value.ArrayValue();
        Slime slime = new Slime();
        slime.setObject().setString("colour","yellow");
        access.add(new SlimeAdapter(slime.get()));
        slime = new Slime();
        slime.setObject().setString("colour","green");
        access.add(new SlimeAdapter(slime.get()));
        subQuery.trace(access, 1);
        q.trace("marker", 1);
        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testEmptyTracing() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
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
    public void testTracingWithEmptySubtree() throws IOException, InterruptedException, ExecutionException {
        String expected =  "{"
                + "    \"root\": {"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    },"
                + "    \"trace\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"message\": \"No query profile is used\""
                + "            },"
                + "            {"
                + "                \"message\": \"Resolved properties:\\ntracelevel: 10 (from request)\\nquery: a (from request)\\n\""
                + "            },"
                + "            {"
                + "                \"children\": ["
                + "                    {"
                + "                        \"timestamp\": 42"
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
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
            traceElement.put("timestamp", 42);
        }
        assertEquals(exp, gen);
    }

    @Test
    public void trace_is_not_included_if_tracelevel_0() throws IOException, ExecutionException, InterruptedException {
        String expected =
                "{" +
                "  \"root\": {" +
                "    \"id\": \"toplevel\"," +
                "    \"relevance\": 1.0," +
                "    \"fields\": {" +
                "      \"totalCount\": 0" +
                "    }" +
                "  }" +
                "}";
        Query q = new Query("/?query=a&tracelevel=0");
        Execution execution = new Execution(Execution.Context.createContextStub());
        Result r = new Result(q);
        execution.search(q);
        q.getContext(true).setProperty("prop-key", "prop-value");
        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testTracingOfNodesWithBothChildrenAndData() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    },"
                + "    \"trace\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"message\": \"No query profile is used\""
                + "            },"
                + "            {"
                + "                \"children\": ["
                + "                    {"
                + "                        \"message\": \"string payload\","
                + "                        \"children\": ["
                + "                            {"
                + "                                \"message\": \"leafnode\""
                + "                            }"
                + "                        ]"
                + "                    },"
                + "                    {"
                + "                        \"message\": \"something\""
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "}";
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
    public void testTracingOfNodesWithBothChildrenAndDataAndEmptySubnode() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    },"
                + "    \"trace\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"message\": \"No query profile is used\""
                + "            },"
                + "            {"
                + "                \"children\": ["
                + "                    {"
                + "                        \"message\": \"string payload\""
                + "                    },"
                + "                    {"
                + "                        \"message\": \"something\""
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "}";
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
    public void testTracingOfNestedNodesWithDataAndSubnodes() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    },"
                + "    \"trace\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"message\": \"No query profile is used\""
                + "            },"
                + "            {"
                + "                \"children\": ["
                + "                    {"
                + "                        \"message\": \"string payload\","
                + "                        \"children\": ["
                + "                            {"
                + "                                \"children\": ["
                + "                                    {"
                + "                                        \"message\": \"in OO languages, nesting is for birds\""
                + "                                    }"
                + "                                ]"
                + "                            }"
                + "                        ]"
                + "                    }"
                + "                ]"
                + "            }"
                + "        ]"
                + "    }"
                + "}";
        Query q = new Query("/?query=a&tracelevel=1");
        Execution execution = new Execution(
                Execution.Context.createContextStub());
        Result r = new Result(q);
        execution.search(q);
        TraceNode child = new TraceNode("string payload", 0L);
        TraceNode childOfChild = new TraceNode(null, 0L);
        child.add(childOfChild);
        childOfChild.add(new TraceNode("in OO languages, nesting is for birds", 0L));
        execution.trace().traceNode().add(child);
        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }


    @Test
    public void test() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"children\": ["
                + "                    {"
                + "                        \"fields\": {"
                + "                            \"c\": \"d\""
                + "                        },"
                + "                        \"id\": \"http://localhost/1\","
                + "                        \"relevance\": 0.9,"
                + "                        \"types\": ["
                + "                            \"summary\""
                + "                        ]"
                + "                    }"
                + "                ],"
                + "                \"id\": \"usual\","
                + "                \"relevance\": 1.0"
                + "            },"
                + "            {"
                + "                \"fields\": {"
                + "                    \"e\": \"f\""
                + "                },"
                + "                \"id\": \"type grouphit\","
                + "                \"relevance\": 1.0,"
                + "                \"types\": ["
                + "                    \"grouphit\""
                + "                ]"
                + "            },"
                + "            {"
                + "                \"fields\": {"
                + "                    \"b\": \"foo\""
                + "                },"
                + "                \"id\": \"http://localhost/\","
                + "                \"relevance\": 0.95,"
                + "                \"types\": ["
                + "                    \"summary\""
                + "                ]"
                + "            }"
                + "        ],"
                + "        \"coverage\": {"
                + "            \"coverage\": 100,"
                + "            \"documents\": 500,"
                + "            \"full\": true,"
                + "            \"nodes\": 1,"
                + "            \"results\": 1,"
                + "            \"resultsFull\": 1"
                + "        },"
                + "        \"errors\": ["
                + "            {"
                + "                \"code\": 18,"
                + "                \"message\": \"boom\","
                + "                \"summary\": \"Internal server error.\""
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Query q = new Query("/?query=a&tracelevel=5");
        Execution execution = new Execution(Execution.Context.createContextStub());
        Result r = new Result(q);
        r.setCoverage(new Coverage(500, 500,1,1));

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
        String expected = "{"
                + "    \"root\": {"
                + "        \"coverage\": {"
                + "            \"coverage\": 83,"
                + "            \"documents\": 500,"
                + "            \"degraded\" : {"
                + "                \"match-phase\" : true,"
                + "                \"timeout\" : false,"
                + "                \"adaptive-timeout\" : true,"
                + "                \"non-ideal-state\" : false"
                + "            },"
                + "            \"full\": false,"
                + "            \"nodes\": 1,"
                + "            \"results\": 1,"
                + "            \"resultsFull\": 0"
                + "        },"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Query q = new Query("/?query=a&tracelevel=5");
        Execution execution = new Execution(Execution.Context.createContextStub());
        Result r = new Result(q);
        r.setCoverage(new Coverage(500, 600).setDegradedReason(5));

        String summary = render(execution, r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testMoreTypes() throws InterruptedException, ExecutionException, IOException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"fields\": {"
                + "                    \"bigDecimal\": 3.402823669209385e+38,"
                + "                    \"bigInteger\": 340282366920938463463374607431768211455,"
                + "                    \"byte\": 8,"
                + "                    \"short\": 16"
                + "                },"
                + "                \"id\": \"moredatatypestuff\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Result r = newEmptyResult();
        Hit h = new Hit("moredatatypestuff");
        h.setField("byte", (byte)8);
        h.setField("short", (short)16);
        h.setField("bigInteger", new BigInteger("340282366920938463463374607431768211455"));
        h.setField("bigDecimal", new BigDecimal("340282366920938463463374607431768211456.5"));
        h.setField("nanNumber", NanNumber.NaN);
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testNullField() throws InterruptedException, ExecutionException, IOException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"fields\": {"
                + "                    \"null\": null"
                + "                },"
                + "                \"id\": \"nullstuff\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
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
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"id\": \"datatypestuff\","
                + "                \"relevance\": 1.0,"
                + "                \"source\": \"unit test\""
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
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
        String expected = "{"
                + "    \"root\": {"
                + "        \"errors\": ["
                + "            {"
                + "                \"code\": 1234,"
                + "                \"message\": \"top of the day\","
                + "                \"stackTrace\": \"java.lang.Throwable\\n\\tat com.yahoo.search.rendering.JsonRendererTestCase.testErrorWithStackTrace(JsonRendererTestCase.java:732)\\n\","
                + "                \"summary\": \"hello\""
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Query q = new Query("/?query=a&tracelevel=5");
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
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"children\": ["
                + "                    {"
                + "                        \"children\": ["
                + "                            {"
                + "                                \"fields\": {"
                + "                                    \"count()\": 7"
                + "                                },"
                + "                                \"value\": \"Jones\","
                + "                                \"id\": \"group:string:Jones\","
                + "                                \"relevance\": 1.0"
                + "                            }"
                + "                        ],"
                + "                        \"continuation\": {"
                + "                            \"next\": \"CCCC\","
                + "                            \"prev\": \"BBBB\""
                + "                        },"
                + "                        \"id\": \"grouplist:customer\","
                + "                        \"label\": \"customer\","
                + "                        \"relevance\": 1.0"
                + "                    }"
                + "                ],"
                + "                \"continuation\": {"
                + "                    \"this\": \"AAAA\""
                + "                },"
                + "                \"id\": \"group:root:0\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Result r = newEmptyResult();
        RootGroup rg = new RootGroup(0, new Continuation() {
            @Override
            public Continuation copy() {
                return null;
            }

            @Override
            public String toString() {
                return "AAAA";
            }
        });
        GroupList gl = new GroupList("customer");
        gl.continuations().put("prev", new Continuation() {
            @Override
            public Continuation copy() {
                return null;
            }

            @Override
            public String toString() {
                return "BBBB";
            }
        });
        gl.continuations().put("next", new Continuation() {
            @Override
            public Continuation copy() {
                return null;
            }

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
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"children\": ["
                + "                    {"
                + "                        \"children\": ["
                + "                            {"
                + "                                \"fields\": {"
                + "                                    \"something()\": 7"
                + "                                },"
                + "                                \"limits\": {"
                + "                                    \"from\": \"1.0\","
                + "                                    \"to\": \"2.0\""
                + "                                },"
                + "                                \"id\": \"group:double_bucket:1.0:2.0\","
                + "                                \"relevance\": 1.0"
                + "                            }"
                + "                        ],"
                + "                        \"id\": \"grouplist:customer\","
                + "                        \"label\": \"customer\","
                + "                        \"relevance\": 1.0"
                + "                    }"
                + "                ],"
                + "                \"continuation\": {"
                + "                    \"this\": \"AAAA\""
                + "                },"
                + "                \"id\": \"group:root:0\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Result r = newEmptyResult();
        RootGroup rg = new RootGroup(0, new Continuation() {
            @Override
            public Continuation copy() {
                return null;
            }

            @Override
            public String toString() {
                return "AAAA";
            }
        });
        GroupList gl = new GroupList("customer");
        Group g = new Group(new DoubleBucketId(1.0, 2.0), new Relevance(1.0));
        g.setField("something()", 7);
        gl.add(g);
        rg.add(gl);
        r.hits().add(rg);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testJsonObjects() throws InterruptedException, ExecutionException, IOException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"fields\": {"
                + "                    \"inspectable\": {"
                + "                        \"a\": \"b\""
                + "                    },"
                + "                    \"jackson\": {"
                + "                        \"Nineteen-eighty-four\": 1984"
                + "                    },"
                + "                    \"json producer\": {"
                + "                        \"long in structured\": 7809531904"
                + "                    }"
                + "                },"
                + "                \"id\": \"json objects\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Result r = newEmptyResult();
        Hit h = new Hit("json objects");
        ObjectNode j = jsonMapper.createObjectNode();
        JSONString s = new JSONString("{\"a\": \"b\"}");
        Slime slime = new Slime();
        Cursor c = slime.setObject();
        c.setLong("long in structured", 7809531904L);
        SlimeAdapter slimeInit = new SlimeAdapter(slime.get());
        StructuredData struct = new StructuredData(slimeInit);
        j.put("Nineteen-eighty-four", 1984);
        h.setField("inspectable", s);
        h.setField("jackson", j);
        h.setField("json producer", struct);
        r.hits().add(h);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testFieldValueInHit() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"fields\": {"
                + "                    \"fromDocumentApi\":{\"integerField\":123, \"stringField\":\"abc\"}"
                + "                },"
                + "                \"id\": \"fieldValueTest\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
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
    public void testHiddenFields() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"id\": \"hiddenFields\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Result r = newEmptyResult();
        Hit h = createHitWithOnlyHiddenFields();
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testDebugRendering() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"fields\": {"
                + "                    \"NaN\": \"NaN\","
                + "                    \"emptyString\": \"\","
                + "                    \"emptyStringFieldValue\": \"\","
                + "                    \"$vespaImplementationDetail\": \"Hello, World!\""
                + "                },"
                + "                \"id\": \"hiddenFields\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Result r = new Result(new Query("/?renderer.json.debug=true"));
        Hit h = createHitWithOnlyHiddenFields();
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testTimingRendering() throws InterruptedException, ExecutionException, IOException {
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
    public void testJsonCallback() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"fields\": {"
                + "                    \"documentid\": \"id:unittest:smoke::whee\""
                + "                },"
                + "                \"id\": \"id:unittest:smoke::whee\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";

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
    public void testMapInField() throws IOException, InterruptedException, ExecutionException {
        String expected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"fields\": {"
                + "                    \"structured\": {"
                + "                        \"foo\": \"string foo\","
                + "                        \"bar\": [\"array bar elem 1\", \"array bar elem 2\"],"
                + "                        \"baz\": {\"f1\": \"object baz field 1\", \"f2\": \"object baz field 2\"}"
                + "                    }"
                + "                },"
                + "                \"id\": \"MapInField\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 1"
                + "        },"
                + "        \"id\": \"toplevel\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        Result r = newEmptyResult();
        Hit h = new Hit("MapInField");
        Value.ArrayValue atop = new Value.ArrayValue();
        atop.add(new Value.ObjectValue()
                 .put("key", new Value.StringValue("foo"))
                 .put("value", new Value.StringValue("string foo")))
            .add(new Value.ObjectValue()
                 .put("key", new Value.StringValue("bar"))
                 .put("value", new Value.ArrayValue()
                      .add(new Value.StringValue("array bar elem 1"))
                      .add(new Value.StringValue("array bar elem 2"))))
            .add(new Value.ObjectValue()
                 .put("key", new Value.StringValue("baz"))
                 .put("value", new Value.ObjectValue()
                      .put("f1", new Value.StringValue("object baz field 1"))
                      .put("f2", new Value.StringValue("object baz field 2"))));
        h.setField("structured", atop);
        r.hits().add(h);
        r.setTotalHitCount(1L);
        String summary = render(r);
        assertEqualJson(expected, summary);
    }

    @Test
    public void testThatTheJsonValidatorCanCatchErrors() {
        String json = "{"
                + "    \"root\": {"
                + "        \"invalidvalue\": 1adsf,"
                + "    }"
                + "}";
        assertEquals(
                "Unexpected character ('a' (code 97)): was expecting comma to separate Object entries\n" +
                        " at [Source: (String)\"{    \"root\": {        \"invalidvalue\": 1adsf,    }}\"; line: 1, column: 41]",
                validateJSON(json));
    }

    @Test
    public void testDynamicSummary() throws Exception {
        String content = "\uFFF9Feeding\uFFFAfeed\uFFFB \u001F\uFFF9documents\uFFFAdocument\uFFFB\u001F into Vespa \uFFF9is\uFFFAbe\u001Eincrement of a set of \u001F\uFFF9documents\uFFFAdocument\uFFFB\u001F fed into Vespa \uFFF9is\u001Efloat in XML when \u001Fdocument\u001F attribute \uFFF9is\uFFFAbe\uFFFB int\u001E";
        Result result = createResult("one", content, true);

        String summary = render(result);

        String expected =
                "{  " +
                "   \"root\":{  " +
                "      \"id\":\"toplevel\"," +
                "      \"relevance\":1.0," +
                "      \"fields\":{  " +
                "         \"totalCount\":0" +
                "      }," +
                "      \"children\":[  " +
                "         {  " +
                "            \"id\":\"http://abc.html/\"," +
                "            \"relevance\":1.0," +
                "            \"fields\":{  " +
                "               \"sddocname\":\"one\"," +
                "               \"dynteaser\":\"Feeding <hi>documents</hi> into Vespa is<sep />increment of a set of <hi>documents</hi> fed into Vespa <sep />float in XML when <hi>document</hi> attribute is int<sep />\"" +
                "            }" +
                "         }" +
                "      ]" +
                "   }" +
                "}";
        assertEqualJson(expected, summary);
    }

    private Result newEmptyResult(String[] args) {
        return new Result(new Query("/?" + String.join("&", args)));
    }

    private Result newEmptyResult() {
        return newEmptyResult(new String[] {"query=a"});
    }

    private Hit createHitWithOnlyHiddenFields() {
        Hit h = new Hit("hiddenFields");
        h.setField("NaN", NanNumber.NaN);
        h.setField("emptyString", "");
        h.setField("emptyStringFieldValue", new StringFieldValue(""));
        h.setField("$vespaImplementationDetail", "Hello, World!");
        return h;
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
        assertEquals("", validateJSON(expected));
        assertEquals("", validateJSON(generated));

        ObjectMapper m = new ObjectMapper();
        Map<String, Object> exp = m.readValue(expected, Map.class);
        Map<String, Object> gen = m.readValue(generated, Map.class);
        assertEquals(exp, gen);
    }

    private String validateJSON(String presumablyValidJson) {
        try {
            jsonMapper.readTree(presumablyValidJson);
            return "";
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private static final class Thingie {
        @Override
        public String toString() {
            return "thingie";
        }
    }

    private Result createResult(String sdName, String content, boolean bolding) {
        Chain<Searcher> chain = createSearchChain(sdName, content);
        Query query = new Query("?query=12");
        if ( ! bolding)
            query = new Query("?query=12&bolding=false");
        Execution execution = createExecution(chain);
        Result result = execution.search(query);
        execution.fill(result);
        return result;
    }

    /**
     * Creates a search chain which always returns a result with one hit containing information given in this
     *
     * @param sdName the search definition type of the returned hit
     * @param content the content of the "dynteaser" field of the returned hit
     */
    private Chain<Searcher> createSearchChain(String sdName, String content) {
        JuniperSearcher searcher = new JuniperSearcher(new ComponentId("test"),
                                                       new QrSearchersConfig(new QrSearchersConfig.Builder()));

        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        addResult(new Query("?query=12"), sdName, content, docsource);
        addResult(new Query("?query=12&bolding=false"), sdName, content, docsource);
        return new Chain<>(searcher, docsource);
    }

    private void addResult(Query query, String sdName, String content, DocumentSourceSearcher docsource) {
        Result r = new Result(query);
        FastHit hit = new FastHit();
        hit.setId("http://abc.html");
        hit.setRelevance(new Relevance(1));
        hit.setField(Hit.SDDOCNAME_FIELD, sdName);
        hit.setField("dynteaser", content);
        r.hits().add(hit);
        docsource.addResult(query, r);
    }

    private Execution createExecution(Chain<Searcher> chain) {
        IndexModel indexModel = new IndexModel(createSearchDefinitionOne());
        return new Execution(chain, Execution.Context.createContextStub(new IndexFacts(indexModel)));
    }

    private SearchDefinition createSearchDefinitionOne() {
        SearchDefinition one = new SearchDefinition("one");

        Index dynteaser = new Index("dynteaser");
        dynteaser.setDynamicSummary(true);
        one.addIndex(dynteaser);

        Index bigteaser = new Index("bigteaser");
        dynteaser.setHighlightSummary(true);
        one.addIndex(bigteaser);

        Index otherteaser = new Index("otherteaser");
        otherteaser.setDynamicSummary(true);
        one.addIndex(otherteaser);

        return one;
    }

}
