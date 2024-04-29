// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.llm;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.EventStream;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class LLMSearcherTest {

    @Test
    public void testLLMSelection() {
        var client1 = createLLMClient("mock1");
        var client2 = createLLMClient("mock2");
        var config = new LlmSearcherConfig.Builder().stream(false).providerId("mock2").build();
        var searcher = createLLMSearcher(config, Map.of("mock1", client1, "mock2", client2));
        var result = runMockSearch(searcher, Map.of("prompt", "what is your id?"));
        assertEquals(1, result.getHitCount());
        assertEquals("My id is mock2", getCompletion(result));
    }

    @Test
    public void testGeneration() {
        var client = createLLMClient();
        var searcher = createLLMSearcher(client);
        var params = Map.of("prompt", "why are ducks better than cats");
        assertEquals("Ducks have adorable waddling walks.", getCompletion(runMockSearch(searcher, params)));
    }

    @Test
    public void testPrompting() {
        var client = createLLMClient();
        var searcher = createLLMSearcher(client);

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
        var client = createLLMClient();
        var searcher = createLLMSearcher(client);
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
        var client = createLLMClient();
        var searcher = createLLMSearcher(client);
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
        var client = createLLMClient();
        var searcher = createLLMSearcher(config, client);
        assertEquals("I have no opinion on", getCompletion(runMockSearch(searcher, params)));
    }

    @Test
    public void testApiKeyFromHeader() {
        var properties = Map.of("prompt", "why are ducks better than cats");
        var client = createLLMClient(createApiKeyGenerator("a_valid_key"));
        var searcher = createLLMSearcher(client);
        assertThrows(IllegalArgumentException.class, () -> runMockSearch(searcher, properties, "invalid_key"));
        assertDoesNotThrow(() -> runMockSearch(searcher, properties, "a_valid_key"));
    }

    @Test
    @Disabled
    public void testAsyncGeneration() {
        var executor = Executors.newFixedThreadPool(2);
        var sb = new StringBuilder();
        try {
            var config = new LlmSearcherConfig.Builder().stream(false).providerId("mock").build(); // config says don't stream...
            var params = Map.of(
                    "llm.stream", "true",  // ... but inference parameters says do it anyway
                    "llm.prompt", "why are ducks better than cats?"
            );
            var client = createLLMClient(executor);
            var searcher = createLLMSearcher(config, client);
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

    static Result runMockSearch(Searcher searcher, Map<String, String> parameters, String apiKey) {
        return runMockSearch(searcher, parameters, apiKey, "llm");
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
        return (prompt, options) -> {
            String answer = "I have no opinion on the matter";
            if (prompt.asString().contains("ducks")) {
                answer = "Ducks have adorable waddling walks.";
                var temperature = options.getDouble("temperature");
                if (temperature.isPresent() && temperature.get() > 0.5) {
                    answer = "Random text about ducks vs cats that makes no sense whatsoever.";
                }
            }
            var maxTokens = options.getInt("maxTokens");
            if (maxTokens.isPresent()) {
                return Arrays.stream(answer.split(" ")).limit(maxTokens.get()).collect(Collectors.joining(" "));
            }
            return answer;
        };
    }

    private static BiFunction<Prompt, InferenceParameters, String> createApiKeyGenerator(String validApiKey) {
        return (prompt, options) -> {
            if (options.getApiKey().isEmpty() || ! options.getApiKey().get().equals(validApiKey)) {
                throw new IllegalArgumentException("Invalid API key");
            }
            return "Ok";
        };
    }

    static MockLLM createLLMClient() {
        return new MockLLM(createGenerator(), null);
    }

    static MockLLM createLLMClient(String id) {
        return new MockLLM(createIdGenerator(id), null);
    }

    static MockLLM createLLMClient(BiFunction<Prompt, InferenceParameters, String> generator) {
        return new MockLLM(generator, null);
    }

    static MockLLM createLLMClient(ExecutorService executor) {
        return new MockLLM(createGenerator(), executor);
    }

    private static Searcher createLLMSearcher(LanguageModel llm) {
        return createLLMSearcher(Map.of("mock", llm));
    }

    private static Searcher createLLMSearcher(Map<String, LanguageModel> llms) {
        var config = new LlmSearcherConfig.Builder().stream(false).build();
        return createLLMSearcher(config, llms);
    }

    private static Searcher createLLMSearcher(LlmSearcherConfig config, LanguageModel llm) {
        return createLLMSearcher(config, Map.of("mock", llm));
    }

    private static Searcher createLLMSearcher(LlmSearcherConfig config, Map<String, LanguageModel> llms) {
        ComponentRegistry<LanguageModel> models = new ComponentRegistry<>();
        llms.forEach((key, value) -> models.register(ComponentId.fromString(key), value));
        models.freeze();
        return new LLMSearcher(config, models);
    }

    private static class MockLLM implements LanguageModel {

        private final ExecutorService executor;
        private final BiFunction<Prompt, InferenceParameters, String> generator;

        public MockLLM(BiFunction<Prompt, InferenceParameters, String> generator, ExecutorService executor) {
            this.executor = executor;
            this.generator = generator;
        }

        @Override
        public List<Completion> complete(Prompt prompt, InferenceParameters params) {
            return List.of(Completion.from(this.generator.apply(prompt, params)));
        }

        @Override
        public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                                        InferenceParameters params,
                                                                        Consumer<Completion> consumer) {
            var completionFuture = new CompletableFuture<Completion.FinishReason>();
            var completions = this.generator.apply(prompt, params).split(" ");  // Simple tokenization

            long sleep = 1;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < completions.length; ++i) {
                        String completion = (i > 0 ? " " : "") + completions[i];
                        consumer.accept(Completion.from(completion, Completion.FinishReason.none));
                        Thread.sleep(sleep);
                    }
                    completionFuture.complete(Completion.FinishReason.stop);
                } catch (InterruptedException e) {
                    // Do nothing
                }
            });
            return completionFuture;
        }

    }

}
