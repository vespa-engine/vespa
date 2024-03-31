// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.search.Query;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.RankProfile.InputType;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests queries towards rank profiles using input declarations.
 *
 * @author bratseth
 */
public class RankProfileInputTest {

    @Test
    void testTensorRankFeatureInRequest() {
        String tensorString = "{{a:a1, b:b1}:1.0, {a:a2, b:b1}:2.0}}";

        {
            Query query = createTensor1Query(tensorString, "commonProfile", "");
            assertEquals(0, query.errors().size());
            assertEquals(Tensor.from(tensorString), query.properties().get("ranking.features.query(myTensor1)"));
            assertEquals(Tensor.from(tensorString), query.getRanking().getFeatures().getTensor("query(myTensor1)").get());
        }

        { // Partial resolution is sufficient
            Query query = createTensor1Query(tensorString, "bOnly", "");
            assertEquals(0, query.errors().size());
            assertEquals(Tensor.from(tensorString), query.properties().get("ranking.features.query(myTensor1)"));
            assertEquals(Tensor.from(tensorString), query.getRanking().getFeatures().getTensor("query(myTensor1)").get());
        }

        try {
            createTensor1Query(tensorString, "bOnly", "sources=a");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not set 'ranking.features.query(myTensor1)' to '{{a:a1, b:b1}:1.0, {a:a2, b:b1}:2.0}}': No profile named 'bOnly' exists in schemas [a]", Exceptions.toMessageString(e));
        }
    }

    @Test
    void testTensorRankFeatureInRequestInconsistentInput() {
        String tensorString = "{{a:a1, b:b1}:1.0, {a:a2, b:b1}:2.0}}";
        try {
            createTensor1Query(tensorString, "inconsistent", "");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not set 'ranking.features.query(myTensor1)' to '{{a:a1, b:b1}:1.0, {a:a2, b:b1}:2.0}}': " +
                         "Conflicting input type declarations for 'query(myTensor1)': " +
                         "Declared as tensor(a{},b{}) in rank profile 'inconsistent' in schema 'a', " +
                         "and as tensor(x[10]) in rank profile 'inconsistent' in schema 'b'",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void testTensorRankFeatureWithSourceResolution() {
        String tensorString = "{{a:a1, b:b1}:1.0, {a:a2, b:b1}:2.0}}";

        {
            createTensor1Query(tensorString, "inconsistent", "sources=a");
            // Success: No exception
        }

        try {
            createTensor1Query(tensorString, "inconsistent", "sources=ab");
            fail("Excpected exception");
        }
        catch (IllegalArgumentException e) {
            // success
        }

        {
            createTensor1Query(tensorString, "inconsistent", "sources=a&restrict=a");
            // Success: No exception
        }
    }

    @Test
    void testTensorRankFeatureSetProgrammatically() {
        String tensorString = "{{a:a1, b:b1}:1.0, {a:a2, b:b1}:2.0}}";
        Query query = new Query.Builder()
                .setSchemaInfo(createSchemaInfo())
                .setQueryProfile(createQueryProfile()) // Use the instantiation path with query profiles
                .setRequest(HttpRequest.createTestRequest("?" +
                        "&ranking=commonProfile",
                        com.yahoo.jdisc.http.HttpRequest.Method.GET))
                .build();

        query.properties().set("ranking.features.query(myTensor1)", Tensor.from(tensorString));
        assertEquals(Tensor.from(tensorString), query.getRanking().getFeatures().getTensor("query(myTensor1)").get());
    }

    @Test
    void testTensorRankFeatureSetProgrammaticallyWithWrongType() {
        Query query = new Query.Builder()
                .setSchemaInfo(createSchemaInfo())
                .setQueryProfile(createQueryProfile()) // Use the instantiation path with query profiles
                .setRequest(HttpRequest.createTestRequest("?" +
                        "&ranking=commonProfile",
                        com.yahoo.jdisc.http.HttpRequest.Method.GET))
                .build();

        String tensorString = "tensor(x[3]):[0.1, 0.2, 0.3]";
        try {
            query.getRanking().getFeatures().put("query(myTensor1)", Tensor.from(tensorString));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not set 'ranking.features.query(myTensor1)' to 'tensor(x[3]):[0.1, 0.2, 0.3]': " +
                    "This input is declared in rank profile 'commonProfile' as tensor(a{},b{})",
                    Exceptions.toMessageString(e));
        }
        try {
            query.properties().set("ranking.features.query(myTensor1)", Tensor.from(tensorString));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not set 'ranking.features.query(myTensor1)' to 'tensor(x[3]):[0.1, 0.2, 0.3]': " +
                    "Require a tensor of type tensor(a{},b{})",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void testUnembeddedTensorRankFeatureInRequest() {
        String text = "text to embed into a tensor";
        Tensor embedding1 = Tensor.from("tensor<float>(x[5]):[3,7,4,0,0]]");
        Tensor embedding2 = Tensor.from("tensor<float>(x[5]):[1,2,3,4,0]]");

        Map<String, Embedder> embedders = Map.of(
                "emb1", new MockEmbedder(text, Language.UNKNOWN, embedding1)
        );
        assertEmbedQuery("embed(" + text + ")", embedding1, embedders);
        assertEmbedQuery("embed('" + text + "')", embedding1, embedders);
        assertEmbedQuery("embed(\"" + text + "\")", embedding1, embedders);
        assertEmbedQuery("embed(emb1, '" + text + "')", embedding1, embedders);
        assertEmbedQuery("embed(emb1, \"" + text + "\")", embedding1, embedders);
        assertEmbedQueryFails("embed(emb2, \"" + text + "\")", embedding1, embedders,
                "Can't find embedder 'emb2'. Available embedder ids are 'emb1'.");

        embedders = Map.of(
                "emb1", new MockEmbedder(text, Language.UNKNOWN, embedding1),
                "emb2", new MockEmbedder(text, Language.UNKNOWN, embedding2)
        );
        assertEmbedQuery("embed(emb1, '" + text + "')", embedding1, embedders);
        assertEmbedQuery("embed(emb2, '" + text + "')", embedding2, embedders);
        assertEmbedQueryFails("embed(emb3, \"" + text + "\")", embedding1, embedders,
                "Can't find embedder 'emb3'. Available embedder ids are 'emb1', 'emb2'.");
        assertEmbedQueryFails("embed(emb3, text)", embedding1, embedders,
                              "Multiple embedders are provided but the string to embed is not quoted. " +
                              "Usage: embed(embedder-id, 'text'). Available embedder ids are 'emb1', 'emb2'.");

        // And with specified language
        embedders = Map.of(
                "emb1", new MockEmbedder(text, Language.ENGLISH, embedding1)
        );
        assertEmbedQuery("embed(" + text + ")", embedding1, embedders, Language.ENGLISH.languageCode());

        embedders = Map.of(
                "emb1", new MockEmbedder(text, Language.ENGLISH, embedding1),
                "emb2", new MockEmbedder(text, Language.UNKNOWN, embedding2)
        );
        assertEmbedQuery("embed(emb1, '" + text + "')", embedding1, embedders, Language.ENGLISH.languageCode());
        assertEmbedQuery("embed(emb2, '" + text + "')", embedding2, embedders, Language.UNKNOWN.languageCode());
    }

    @Test
    void testUnembeddedTensorRankFeatureInRequestReferencedFromAParameter() {
        String text = "text to embed into a tensor";
        Tensor embedding1 = Tensor.from("tensor<float>(x[5]):[3,7,4,0,0]]");

        Map<String, Embedder> embedders = Map.of(
                "emb1", new MockEmbedder(text, Language.UNKNOWN, embedding1)
                                                );
        assertEmbedQuery("embed(@param1)", embedding1, embedders, null, text);
        assertEmbedQuery("embed(emb1, @param1)", embedding1, embedders, null, text);
        assertEmbedQueryFails("embed(emb1, @noSuchParam)", embedding1, embedders,
                              "Could not resolve query parameter reference 'noSuchParam' " +
                              "used in an embed() argument");
    }

    @Test
    void testUnembeddedTensorRankFeatureInRequestReferencedFromAParameterSuppliedByQueryProfile() {
        String text = "text to embed into a tensor";

        var registry = new QueryProfileRegistry();
        var profile = new QueryProfile("test");
        profile.set("param2", "text to %{param1}", registry);
        registry.register(profile);
        var cProfile = registry.compile().findQueryProfile("test");

        Tensor embedding1 = Tensor.from("tensor<float>(x[5]):[3,7,4,0,0]]");

        Map<String, Embedder> embedders = Map.of(
                "emb1", new MockEmbedder(text, Language.UNKNOWN, embedding1)
                                                );
        assertEmbedQuery("embed(@param2)", embedding1, embedders, null, "embed into a tensor", cProfile);
        assertEmbedQuery("embed(emb1, @param2)", embedding1, embedders, null, "embed into a tensor", cProfile);
        assertEmbedQueryFails("embed(emb1, @noSuchParam)", embedding1, embedders,
                              "Could not resolve query parameter reference 'noSuchParam' " +
                              "used in an embed() argument");
    }

    private Query createTensor1Query(String tensorString, String profile, String additionalParams) {
        return new Query.Builder()
                .setSchemaInfo(createSchemaInfo())
                .setQueryProfile(createQueryProfile()) // Use the instantiation path with query profiles
                .setRequest(HttpRequest.createTestRequest("?" + urlEncode("input.query(myTensor1)") +
                                                          "=" + urlEncode(tensorString) +
                                                          "&ranking=" + profile +
                                                          "&" + additionalParams,
                                                          com.yahoo.jdisc.http.HttpRequest.Method.GET))
                .build();
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private void assertEmbedQuery(String embed, Tensor expected, Map<String, Embedder> embedders) {
        assertEmbedQuery(embed, expected, embedders, null, null);
    }

    private void assertEmbedQuery(String embed, Tensor expected, Map<String, Embedder> embedders, String language) {
        assertEmbedQuery(embed, expected, embedders, language, null);
    }
    private void assertEmbedQuery(String embed,
                                  Tensor expected,
                                  Map<String, Embedder> embedders,
                                  String language,
                                  String param1Value) {
        assertEmbedQuery(embed, expected, embedders, language, param1Value, null);
    }
    private void assertEmbedQuery(String embed,
                                  Tensor expected,
                                  Map<String, Embedder> embedders,
                                  String language,
                                  String param1Value,
                                  CompiledQueryProfile queryProfile) {
        String languageParam = language == null ? "" : "&language=" + language;
        String param1 = param1Value == null ? "" : "&param1=" + urlEncode(param1Value);

        String destination = "query(myTensor4)";

        Query query = new Query.Builder().setRequest(HttpRequest.createTestRequest(
                                                 "?" + urlEncode("ranking.features." + destination) +
                                                 "=" + urlEncode(embed) +
                                                 "&ranking=commonProfile" +
                                                 languageParam +
                                                 param1,
                                                 com.yahoo.jdisc.http.HttpRequest.Method.GET))
                                         .setSchemaInfo(createSchemaInfo())
                                         .setQueryProfile(createQueryProfile())
                                         .setEmbedders(embedders)
                                         .setQueryProfile(queryProfile)
                                         .build();
        assertEquals(0, query.errors().size());
        assertEquals(expected, query.properties().get("ranking.features." + destination));
        assertEquals(expected, query.getRanking().getFeatures().getTensor(destination).get());
    }

    private void assertEmbedQueryFails(String embed, Tensor expected, Map<String, Embedder> embedders, String errMsg) {
        Throwable t = assertThrows(IllegalArgumentException.class, () -> assertEmbedQuery(embed, expected, embedders));
        while (t != null) {
            if (t.getMessage().equals(errMsg)) return;
            t = t.getCause();
        }
        fail("Exception with message '" + errMsg + "' not thrown");
    }

    private CompiledQueryProfile createQueryProfile() {
        var registry = new QueryProfileRegistry();
        registry.register(new QueryProfile("test"));
        return registry.compile().findQueryProfile("test");
    }

    private SchemaInfo createSchemaInfo() {
        List<Schema> schemas = new ArrayList<>();
        RankProfile.Builder common = new RankProfile.Builder("commonProfile")
                .addInput("query(myTensor1)", InputType.fromSpec("tensor(a{},b{})"))
                .addInput("query(myTensor2)", InputType.fromSpec("tensor(x[2],y[2])"))
                .addInput("query(myTensor3)", InputType.fromSpec("tensor(x[2],y[2])"))
                .addInput("query(myTensor4)", InputType.fromSpec("tensor<float>(x[5])"));
        schemas.add(new Schema.Builder("a")
                            .add(common.build())
                            .add(new RankProfile.Builder("inconsistent")
                                         .addInput("query(myTensor1)", InputType.fromSpec("tensor(a{},b{})"))
                                         .build())
                            .build());
        schemas.add(new Schema.Builder("b")
                            .add(common.build())
                            .add(new RankProfile.Builder("inconsistent")
                                         .addInput("query(myTensor1)", InputType.fromSpec("tensor(x[10])"))
                                         .build())
                            .add(new RankProfile.Builder("bOnly")
                                         .addInput("query(myTensor1)", InputType.fromSpec("tensor(a{},b{})"))
                                         .build())
                            .build());
        List<Cluster> clusters = new ArrayList<>();
        clusters.add(new Cluster.Builder("ab").addSchema("a").addSchema("b").build());
        clusters.add(new Cluster.Builder("a").addSchema("a").build());
        return new SchemaInfo(schemas, clusters);
    }

    private static final class MockEmbedder implements Embedder {

        private final String expectedText;
        private final Language expectedLanguage;
        private final Tensor tensorToReturn;

        public MockEmbedder(String expectedText,
                            Language expectedLanguage,
                            Tensor tensorToReturn) {
            this.expectedText = expectedText;
            this.expectedLanguage = expectedLanguage;
            this.tensorToReturn = tensorToReturn;
        }

        @Override
        public List<Integer> embed(String text, Embedder.Context context) {
            fail("Unexpected call");
            return null;
        }

        @Override
        public Tensor embed(String text, Embedder.Context context, TensorType tensorType) {
            assertEquals(expectedText, text);
            assertEquals(expectedLanguage, context.getLanguage());
            assertEquals(tensorToReturn.type(), tensorType);
            return tensorToReturn;
        }

    }

}
