package ai.vespa.search.llm.interfaces;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import ai.vespa.search.llm.LlmInterfaceConfig;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.jdisc.SecretStoreProvider;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class LLMInterfaceTest {

    @Test
    public void testSyncGeneration() {
        var prompt = StringPrompt.from("Why are ducks better than cats?");
        var result = createLLM().complete(prompt, emptyParams());
        assertEquals(1, result.size());
        assertEquals("Ducks have adorable waddling walks.", result.get(0).text());
    }

    @Test
    public void testAsyncGeneration() {
        var executor = Executors.newFixedThreadPool(1);
        var prompt = StringPrompt.from("Why are ducks better than cats?");
        var sb = new StringBuilder();
        try {
            var future = createLLM(executor).completeAsync(prompt, emptyParams(), completion -> {
                sb.append(completion.text());
            }).exceptionally(exception -> Completion.FinishReason.error);

            assertFalse(future.isDone());
            var reason = future.join();
            assertTrue(future.isDone());
            assertNotEquals(reason, Completion.FinishReason.error);
        } finally {
            executor.shutdownNow();
        }

        assertEquals("Ducks have adorable waddling walks.", sb.toString());
    }

    @Test
    public void testNoApiKey() {
        var prompt = StringPrompt.from("");
        var config = createModelParameters("api-key", null);
        var secrets = createSecretStore(Map.of());
        assertThrows(IllegalArgumentException.class, () -> {
            createLLM(config, createGenerator(), secrets).complete(prompt, emptyParams(null));
        });
    }

    @Test
    public void testApiKeyFromSecretStore() {
        var prompt = StringPrompt.from("");
        var config = createModelParameters("api-key-in-secret-store", null);
        var secrets = createSecretStore(Map.of("api-key-in-secret-store", MockLLMInterface.ACCEPTED_API_KEY));
        assertDoesNotThrow(() -> { createLLM(config, createGenerator(), secrets).complete(prompt, emptyParams()); });
    }

    @Test
    public void testApiKeyFromOptions() {
        var prompt = StringPrompt.from("");
        var config = createModelParameters("api-key", null);
        var secrets = createSecretStore(Map.of("api-key", "invalid"));
        assertDoesNotThrow(() -> { createLLM(config, createGenerator(), secrets).complete(prompt, emptyParams()); });
    }

    @Test
    public void testInferenceParameters() {
        var prompt = StringPrompt.from("Why are ducks better than cats?");
        var params = createInferenceParameters(Map.of("temperature", "1.0", "maxTokens", "4"));
        var result = createLLM().complete(prompt, params);
        assertEquals("Random text about ducks", result.get(0).text());
    }


    private static String lookupParameter(String parameter, Map<String, String> params) {
        return params.get(parameter);
    }

    private static InferenceParameters emptyParams() {
        return new InferenceParameters(MockLLMInterface.ACCEPTED_API_KEY, s -> lookupParameter(s, Collections.emptyMap()));
    }

    private static InferenceParameters emptyParams(String apiKey) {
        return new InferenceParameters(apiKey, s -> lookupParameter(s, Collections.emptyMap()));
    }

    private static InferenceParameters createInferenceParameters(Map<String, String> params) {
        return new InferenceParameters(MockLLMInterface.ACCEPTED_API_KEY, s -> lookupParameter(s, params));
    }

    private LlmInterfaceConfig createModelParameters(String apiKey, String endpoint) {
        var config = new LlmInterfaceConfig.Builder();
        if (apiKey != null) {
            config.apiKey(apiKey);
        }
        if (endpoint != null) {
            config.endpoint(endpoint);
        }
        return config.build();
    }

    public static SecretStore createSecretStore(Map<String, String> secrets) {
        Provider<SecretStore> secretStore = new Provider<>() {
            public SecretStore get() {
                return new SecretStore() {
                    public String getSecret(String key) {
                        return secrets.get(key);
                    }
                    public String getSecret(String key, int version) {
                        return secrets.get(key);
                    }
                };
            }
            public void deconstruct() {
            }
        };
        return secretStore.get();
    }

    public static BiFunction<Prompt, InferenceParameters, String> createGenerator() {
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

    private static MockLLMInterface createLLM() {
        LlmInterfaceConfig config = new LlmInterfaceConfig.Builder().build();
        return createLLM(config, null);
    }

    private static MockLLMInterface createLLM(ExecutorService executor) {
        LlmInterfaceConfig config = new LlmInterfaceConfig.Builder().build();
        return createLLM(config, executor);
    }

    private static MockLLMInterface createLLM(LlmInterfaceConfig config, ExecutorService executor) {
        var generator = createGenerator();
        var secretStore = new SecretStoreProvider();  // throws exception on use
        return createLLM(config, generator, secretStore.get(), executor);
    }

    private static MockLLMInterface createLLM(LlmInterfaceConfig config,
                                              BiFunction<Prompt, InferenceParameters, String> generator,
                                              SecretStore secretStore) {
        return createLLM(config, generator, secretStore, null);
    }

    private static MockLLMInterface createLLM(LlmInterfaceConfig config,
                                              BiFunction<Prompt, InferenceParameters, String> generator,
                                              SecretStore secretStore,
                                              ExecutorService executor) {
        return new MockLLMInterface(config, secretStore, generator, executor);
    }

}
