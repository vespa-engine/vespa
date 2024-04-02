// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.search;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.LlmClientConfig;
import ai.vespa.llm.LlmSearcherConfig;
import ai.vespa.llm.clients.ConfigurableLanguageModelTest;
import ai.vespa.llm.clients.MockLLMClient;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.SecretStoreProvider;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.EventStream;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class LLMSearcherTest {

    @Test
    public void testLLMSelection() {
        var llm1 = createLLMClient("mock1");
        var llm2 = createLLMClient("mock2");
        var config = new LlmSearcherConfig.Builder().stream(false).providerId("mock2").build();
        var searcher = createLLMSearcher(config, Map.of("mock1", llm1, "mock2", llm2));
        var result = runMockSearch(searcher, Map.of("prompt", "what is your id?"));
        assertEquals(1, result.getHitCount());
        assertEquals("My id is mock2", getCompletion(result));
    }

    @Test
    public void testGeneration() {
        var searcher = createLLMSearcher(Map.of("mock", createLLMClient()));
        var params = Map.of("prompt", "why are ducks better than cats");
        assertEquals("Ducks have adorable waddling walks.", getCompletion(runMockSearch(searcher, params)));
    }

    @Test
    public void testPrompting() {
        var searcher = createLLMSearcher(Map.of("mock", createLLMClient()));

        // Prompt with prefix
        assertEquals("Ducks have adorable waddling walks.",
                getCompletion(runMockSearch(searcher, Map.of("llm.prompt", "why are ducks better than cats"))));

        // Prompt without prefix
        assertEquals("Ducks have adorable waddling walks.",
                getCompletion(runMockSearch(searcher, Map.of("prompt", "why are ducks better than cats"))));

        // Fallback to query if not given
        assertEquals("Ducks have adorable waddling walks.",
                getCompletion(runMockSearch(searcher, Map.of("query", "why are ducks better than cats"))));
    }

    @Test
    public void testPromptEvent() {
        var searcher = createLLMSearcher(Map.of("mock", createLLMClient()));
        var params = Map.of(
                "prompt", "why are ducks better than cats",
                "traceLevel", "1");
        var result = runMockSearch(searcher, params);
        var events = ((EventStream) result.hits().get(0)).incoming().drain();
        assertEquals(2, events.size());

        var promptEvent = (EventStream.Event) events.get(0);
        assertEquals("prompt", promptEvent.type());
        assertEquals("why are ducks better than cats", promptEvent.toString());

        var completionEvent = (EventStream.Event) events.get(1);
        assertEquals("completion", completionEvent.type());
        assertEquals("Ducks have adorable waddling walks.", completionEvent.toString());
    }

    @Test
    public void testParameters() {
        var searcher = createLLMSearcher(Map.of("mock", createLLMClient()));
        var params = Map.of(
                "llm.prompt", "why are ducks better than cats",
                "llm.temperature", "1.0",
                "llm.maxTokens", "5"
        );
        assertEquals("Random text about ducks vs", getCompletion(runMockSearch(searcher, params)));
    }

    @Test
    public void testParameterPrefix() {
        var prefix = "foo";
        var params = Map.of(
                "foo.prompt", "what is your opinion on cats",
                "foo.maxTokens", "5"
        );
        var config = new LlmSearcherConfig.Builder().stream(false).propertyPrefix(prefix).providerId("mock").build();
        var searcher = createLLMSearcher(config, Map.of("mock", createLLMClient()));
        assertEquals("I have no opinion on", getCompletion(runMockSearch(searcher, params)));
    }

    @Test
    public void testApiKeyFromHeader() {
        var properties = Map.of("prompt", "why are ducks better than cats");
        var searcher = createLLMSearcher(Map.of("mock", createLLMClientWithoutSecretStore()));
        assertThrows(IllegalArgumentException.class, () -> runMockSearch(searcher, properties, "invalid_key", "llm"));
        assertDoesNotThrow(() -> runMockSearch(searcher, properties, MockLLMClient.ACCEPTED_API_KEY, "llm"));
    }

    @Test
    public void testAsyncGeneration() {
        var executor = Executors.newFixedThreadPool(2);
        var sb = new StringBuilder();
        try {
            var config = new LlmSearcherConfig.Builder().stream(false).providerId("mock").build(); // config says don't stream...
            var params = Map.of(
                    "llm.stream", "true",  // ... but inference parameters says do it anyway
                    "llm.prompt", "why are ducks better than cats?"
            );
            var searcher = createLLMSearcher(config, Map.of("mock", createLLMClient(executor)));
            Result result = runMockSearch(searcher, params);

            assertEquals(1, result.getHitCount());
            assertTrue(result.hits().get(0) instanceof EventStream);
            EventStream eventStream = (EventStream) result.hits().get(0);

            var incoming = eventStream.incoming();
            incoming.addNewDataListener(() -> {
                incoming.drain().forEach(event -> sb.append(event.toString()));
            }, executor);

            incoming.completedFuture().join();
            assertTrue(incoming.isComplete());

            // Ensure incoming has been fully drained to avoid race condition in this test
            incoming.drain().forEach(event -> sb.append(event.toString()));

        } finally {
            executor.shutdownNow();
        }
        assertEquals("Ducks have adorable waddling walks.", sb.toString());
    }

    private static String getCompletion(Result result) {
        assertTrue(result.hits().size() >= 1);
        return ((EventStream) result.hits().get(0)).incoming().drain().get(0).toString();
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

    private static BiFunction<Prompt, InferenceParameters, String> createGenerator() {
        return ConfigurableLanguageModelTest.createGenerator();
    }

    static MockLLMClient createLLMClient() {
        var config = new LlmClientConfig.Builder().apiKeySecretName("api-key").build();
        var secretStore = ConfigurableLanguageModelTest.createSecretStore(Map.of("api-key", MockLLMClient.ACCEPTED_API_KEY));
        var generator = createGenerator();
        return new MockLLMClient(config, secretStore, generator, null);
    }

    static MockLLMClient createLLMClient(String id) {
        var config = new LlmClientConfig.Builder().apiKeySecretName("api-key").build();
        var secretStore = ConfigurableLanguageModelTest.createSecretStore(Map.of("api-key", MockLLMClient.ACCEPTED_API_KEY));
        var generator = createIdGenerator(id);
        return new MockLLMClient(config, secretStore, generator, null);
    }

    static MockLLMClient createLLMClient(ExecutorService executor) {
        var config = new LlmClientConfig.Builder().apiKeySecretName("api-key").build();
        var secretStore = ConfigurableLanguageModelTest.createSecretStore(Map.of("api-key", MockLLMClient.ACCEPTED_API_KEY));
        var generator = createGenerator();
        return new MockLLMClient(config, secretStore, generator, executor);
    }

    static MockLLMClient createLLMClientWithoutSecretStore() {
        var config = new LlmClientConfig.Builder().apiKeySecretName("api-key").build();
        var secretStore = new SecretStoreProvider();
        var generator = createGenerator();
        return new MockLLMClient(config, secretStore.get(), generator, null);
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
            return complete(query, StringPrompt.from(getPrompt(query)));
        }
    }

}
