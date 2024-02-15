// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.search.llm;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import ai.vespa.search.llm.interfaces.LLMInterfaceTest;
import ai.vespa.search.llm.interfaces.MockLLMInterface;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.SecretStoreProvider;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class LLMSearcherTest {

    @Test
    public void testLLMSelection() {
        var llm1 = createLLMInterface("mock1");
        var llm2 = createLLMInterface("mock2");
        var config = new LlmSearcherConfig.Builder().stream(false).providerId("mock2").build();
        var searcher = createLLMSearcher(config, Map.of("mock1", llm1, "mock2", llm2));
        var result = runMockSearch(searcher, Map.of("llm.prompt", "what is your id?"));
        assertEquals(1, result.getHitCount());
        assertEquals("My id is mock2", result.hits().get(0).getField("completion").toString());
    }

    @Test
    public void testApiKeyFromHeader() {
        var properties = Map.of("llm.prompt", "why are ducks better than cats");
        var searcher = createLLMSearcher(Map.of("mock", createLLMInterfaceWithoutSecretStore()));
        assertThrows(IllegalArgumentException.class, () -> runMockSearch(searcher, properties, "invalid_key", "llm"));
        assertDoesNotThrow(() -> runMockSearch(searcher, properties, MockLLMInterface.ACCEPTED_API_KEY, "llm"));
    }

    @Test
    void testParameters() {
        var searcher = createLLMSearcher(Map.of("mock", createLLMInterface()));
        var params = Map.of(
                "llm.prompt", "why are ducks better than cats",
                "llm.temperature", "1.0",
                "llm.maxTokens", "5"
        );
        var result = runMockSearch(searcher, params);
        assertEquals("Random text about ducks vs", result.hits().get(0).getField("completion").toString());
    }

    @Test
    public void testParameterPrefix() {
        var prefix = "foo";
        var params = Map.of(
                "foo.prompt", "what is your opinion on cats",
                "foo.maxTokens", "5"
        );
        var config = new LlmSearcherConfig.Builder().stream(false).propertyPrefix(prefix).build();
        var searcher = createLLMSearcher(config, Map.of("mock", createLLMInterface()));
        var result = runMockSearch(searcher, params);
        assertEquals("I have no opinion on", result.hits().get(0).getField("completion").toString());
    }

    @Test
    public void testAsyncGeneration() {
        var executor = Executors.newFixedThreadPool(1);
        var sb = new StringBuilder();
        try {
            var config = new LlmSearcherConfig.Builder().stream(false).build(); // config says don't stream...
            var params = Map.of(
                    "llm.stream", "true",  // ... but inference parameters says do it anyway
                    "llm.prompt", "why are ducks better than cats?"
            );
            var searcher = createLLMSearcher(config, Map.of("mock", createLLMInterface(executor)));
            Result result = runMockSearch(searcher, params);

            assertEquals(0, result.getHitCount());

            var incoming = result.hits().incoming();
            incoming.addNewDataListener(() -> {
                incoming.drain().forEach(hit -> sb.append(hit.getField("token")));
            }, executor);

            assertFalse(incoming.isComplete());
            incoming.completedFuture().join();
            assertTrue(incoming.isComplete());

        } finally {
            executor.shutdownNow();
        }
        assertEquals("Ducks have adorable waddling walks.", sb.toString());
    }

    static Result runMockSearch(Searcher searcher, Map<String, String> parameters) {
        return runMockSearch(searcher, parameters, null, "");
    }

    static Result runMockSearch(Searcher searcher, Map<String, String> parameters, String apiKey, String prefix) {
        Chain<Searcher> chain = new Chain<>(searcher);
        Execution execution = new Execution(chain, Execution.Context.createContextStub());
        Query query = new Query("?" + toUrlParams(parameters));
        if (apiKey != null) {
            String headerKey = "X-LLM-API-KEY";
            if (prefix != null && ! prefix.isEmpty()) {
                headerKey = prefix + "." + headerKey;
            }
            query.getHttpRequest().getJDiscRequest().headers().add(headerKey, apiKey);
        }
        return execution.search(query);
    }

    public static String toUrlParams(Map<String, String> parameters) {
        return parameters.entrySet().stream().map(
                e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)
        ).collect(Collectors.joining("&"));
    }

    private static BiFunction<Prompt, InferenceParameters, String> createIdGenerator(String id) {
        return (prompt, options) -> {
            if (id == null || id.isEmpty())
                return "I have no ID";
            return "My id is " + id;
        };
    }

    static BiFunction<Prompt, InferenceParameters, String> createGenerator() {
        return LLMInterfaceTest.createGenerator();
    }

    static MockLLMInterface createLLMInterface() {
        var config = new LlmInterfaceConfig.Builder().apiKey("api-key").build();
        var secretStore = LLMInterfaceTest.createSecretStore(Map.of("api-key", MockLLMInterface.ACCEPTED_API_KEY));
        var generator = createGenerator();
        return new MockLLMInterface(config, secretStore, generator, null);
    }

    static MockLLMInterface createLLMInterface(String id) {
        var config = new LlmInterfaceConfig.Builder().apiKey("api-key").build();
        var secretStore = LLMInterfaceTest.createSecretStore(Map.of("api-key", MockLLMInterface.ACCEPTED_API_KEY));
        var generator = createIdGenerator(id);
        return new MockLLMInterface(config, secretStore, generator, null);
    }

    static MockLLMInterface createLLMInterface(ExecutorService executor) {
        var config = new LlmInterfaceConfig.Builder().apiKey("api-key").build();
        var secretStore = LLMInterfaceTest.createSecretStore(Map.of("api-key", MockLLMInterface.ACCEPTED_API_KEY));
        var generator = createGenerator();
        return new MockLLMInterface(config, secretStore, generator, executor);
    }

    static MockLLMInterface createLLMInterfaceWithoutSecretStore() {
        var config = new LlmInterfaceConfig.Builder().apiKey("api-key").build();
        var secretStore = new SecretStoreProvider();
        var generator = createGenerator();
        return new MockLLMInterface(config, secretStore.get(), generator, null);
    }

    private static Searcher createLLMSearcher(Map<String, LanguageModel> llms) {
        var config = new LlmSearcherConfig.Builder().stream(false).build();
        ComponentRegistry<LanguageModel> models = new ComponentRegistry<>();
        llms.forEach((key, value) -> models.register(ComponentId.fromString(key), value));
        models.freeze();
        return new LLMSearcherImpl(config, models);
    }

    private static Searcher createLLMSearcher(LlmSearcherConfig config, Map<String, LanguageModel> llms) {
        ComponentRegistry<LanguageModel> models = new ComponentRegistry<>();
        llms.forEach((key, value) -> models.register(ComponentId.fromString(key), value));
        models.freeze();
        return new LLMSearcherImpl(config, models);
    }

    public static class LLMSearcherImpl extends LLMSearcher {

        public LLMSearcherImpl(LlmSearcherConfig config, ComponentRegistry<LanguageModel> languageModels) {
            super(config, languageModels);
        }

        @Override
        public Result search(Query query, Execution execution) {
            String propertyWithPrefix = this.getPropertyPrefix() + ".prompt";
            String prompt = query.properties().getString(propertyWithPrefix);
            return complete(query, StringPrompt.from(prompt));
        }
    }

}
