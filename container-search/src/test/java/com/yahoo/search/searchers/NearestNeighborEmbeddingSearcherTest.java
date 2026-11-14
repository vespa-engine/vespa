// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.language.process.Embedder;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.yql.YqlParser;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author bratseth
 */
public class NearestNeighborEmbeddingSearcherTest {

    @Test
    void text_is_embedded_with_the_embedder_of_the_searched_field() {
        var tester = new Tester();
        var e1Embedding = Tester.embedding("e1", "my text");
        var e2Embedding = Tester.embedding("e2", "my text");
        assertNotEquals(e1Embedding, e2Embedding);

        var result1 = tester.search("{targetHits:10}nearestNeighbor(embedding1, q)", "input.query(q)=my text");
        assertNull(result1.hits().getError());
        assertEquals(e1Embedding, tester.queryTensor(result1, "query(q)"));

        var result2 = tester.search("{targetHits:10}nearestNeighbor(embedding2, q)", "input.query(q)=my text");
        assertNull(result2.hits().getError());
        assertEquals(e2Embedding, tester.queryTensor(result2, "query(q)"));
    }

    @Test
    void text_is_resolved_from_a_reference() {
        var tester = new Tester();
        var result = tester.search("{targetHits:10}nearestNeighbor(embedding1, q)", "input.query(q)=@text", "text=my text");
        assertNull(result.hits().getError());
        assertEquals(Tester.embedding("e1", "my text"), tester.queryTensor(result, "query(q)"));
    }

    @Test
    void quoted_text_is_unquoted() {
        var tester = new Tester();
        var result = tester.search("{targetHits:10}nearestNeighbor(embedding1, q)", "input.query(q)='my text'");
        assertNull(result.hits().getError());
        assertEquals(Tester.embedding("e1", "my text"), tester.queryTensor(result, "query(q)"));
    }

    @Test
    void tensors_and_embed_expressions_are_unchanged() {
        var tester = new Tester();
        var result = tester.search("{targetHits:10}nearestNeighbor(embedding2, q)", "input.query(q)=[1,2,3]");
        assertNull(result.hits().getError());
        assertEquals(Tensor.from("tensor<float>(x[3]):[1,2,3]"), tester.queryTensor(result, "query(q)"));

        result = tester.search("{targetHits:10}nearestNeighbor(embedding2, q)", "input.query(q)=embed(e1, 'my text')");
        assertNull(result.hits().getError());
        assertEquals(Tester.embedding("e1", "my text"), tester.queryTensor(result, "query(q)"));
    }

    @Test
    void text_must_be_used_with_a_field_which_is_embedded() {
        var tester = new Tester();
        var result = tester.search("{targetHits:10}nearestNeighbor(notEmbedded, q)", "input.query(q)=my text");
        assertEquals("Could not set 'query(q)' to 'my text': This is not a valid tensor<float>(x[3]) " +
                     "(Could not parse 'my text' as a tensor of type tensor<float>(x[3]): " +
                     "Expected a number, hex string, or a string starting by {, [ or tensor(...))" +
                     ", and it is not the query tensor of a nearestNeighbor item searching a field which is embedded",
                     result.hits().getError().getDetailedMessage());
    }

    @Test
    void text_cannot_be_used_with_fields_embedded_differently() {
        var tester = new Tester();
        var result = tester.search("({targetHits:10}nearestNeighbor(embedding1, q)) or ({targetHits:10}nearestNeighbor(embedding2, q))",
                                   "input.query(q)=my text");
        assertEquals("The text of 'query(q)' is searched in fields which are embedded differently: " +
                     "Using both embed(e1, @input) and embed(e2, @input)",
                     result.hits().getError().getDetailedMessage());
    }

    private static class Tester {

        private final SchemaInfo schemaInfo = createSchemaInfo();
        private final Map<String, Embedder> embedders = Map.of("e1", new MockEmbedder("e1"),
                                                               "e2", new MockEmbedder("e2"));
        private final NearestNeighborEmbeddingSearcher searcher = new NearestNeighborEmbeddingSearcher(schemaInfo, embedders);

        Result search(String where, String ... parameters) {
            String request = "?ranking=p";
            for (String parameter : parameters) {
                String[] nameAndValue = parameter.split("=", 2);
                request += "&" + encode(nameAndValue[0]) + "=" + encode(nameAndValue[1]);
            }
            Query query = new Query.Builder().setRequest(HttpRequest.createTestRequest(request, com.yahoo.jdisc.http.HttpRequest.Method.GET))
                                             .setSchemaInfo(schemaInfo)
                                             .setEmbedders(embedders)
                                             .build();
            var queryTree = new YqlParser(new ParserEnvironment()).parse(new Parsable().setQuery("select * from sources * where " + where));
            query.getModel().getQueryTree().setRoot(queryTree.getRoot());
            return new Execution(searcher, Execution.Context.createContextStub()).search(query);
        }

        Tensor queryTensor(Result result, String feature) {
            return result.getQuery().getRanking().getFeatures().getTensor(feature).get();
        }

        static Tensor embedding(String embedderId, String text) {
            return Tensor.from("tensor<float>(x[3]):[" + embedderId.hashCode() % 100 + "," + text.length() + ",0]");
        }

        private static String encode(String s) {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        }

        private static SchemaInfo createSchemaInfo() {
            var schema = new Schema.Builder("test")
                                 .add(new Field.Builder("embedding1", "tensor<float>(x[3])").setAttribute(true)
                                                                                            .setInputExpression("embed(e1, @input)").build())
                                 .add(new Field.Builder("embedding2", "tensor<float>(x[3])").setAttribute(true)
                                                                                            .setInputExpression("embed(e2, @input)").build())
                                 .add(new Field.Builder("notEmbedded", "tensor<float>(x[3])").setAttribute(true).build())
                                 .add(new RankProfile.Builder("p").addInput("query(q)", RankProfile.InputType.fromSpec("tensor<float>(x[3])"))
                                                                  .build())
                                 .build();
            return new SchemaInfo(List.of(schema), List.of(new Cluster.Builder("c").addSchema("test").build()));
        }

    }

    private record MockEmbedder(String id) implements Embedder {

        @Override
        public List<Integer> embed(String text, Context context) { throw new UnsupportedOperationException(); }

        @Override
        public Tensor embed(String text, Context context, TensorType tensorType) {
            assertEquals(id, context.getEmbedderId());
            return Tester.embedding(id, text);
        }

    }

}
