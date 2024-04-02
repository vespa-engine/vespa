// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LlmClientConfig;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigurableLanguageModelTest {

    @Test
    public void testSyncGeneration() {
        var prompt = StringPrompt.from("Why are ducks better than cats?");
        var result = createLLM().complete(prompt, inferenceParamsWithDefaultKey());
        assertEquals(1, result.size());
        assertEquals("Ducks have adorable waddling walks.", result.get(0).text());
    }

    @Test
    public void testAsyncGeneration() {
        var executor = Executors.newFixedThreadPool(1);
        var prompt = StringPrompt.from("Why are ducks better than cats?");
        var sb = new StringBuilder();
        try {
            var future = createLLM(executor).completeAsync(prompt, inferenceParamsWithDefaultKey(), completion -> {
                sb.append(completion.text());
            }).exceptionally(exception -> Completion.FinishReason.error);

            var reason = future.join();
            assertTrue(future.isDone());
            assertNotEquals(reason, Completion.FinishReason.error);
        } finally {
            executor.shutdownNow();
        }

        assertEquals("Ducks have adorable waddling walks.", sb.toString());
    }

    @Test
    public void testInferenceParameters() {
        var prompt = StringPrompt.from("Why are ducks better than cats?");
        var params = inferenceParams(Map.of("temperature", "1.0", "maxTokens", "4"));
        var result = createLLM().complete(prompt, params);
        assertEquals("Random text about ducks", result.get(0).text());
    }

    @Test
    public void testNoApiKey() {
        var prompt = StringPrompt.from("");
        var config = modelParams("api-key", null);
        var secrets = createSecretStore(Map.of());
        assertThrows(IllegalArgumentException.class, () -> {
            createLLM(config, createGenerator(), secrets).complete(prompt, inferenceParams());
        });
    }

    @Test
    public void testApiKeyFromSecretStore() {
        var prompt = StringPrompt.from("");
        var config = modelParams("api-key-in-secret-store", null);
        var secrets = createSecretStore(Map.of("api-key-in-secret-store", MockLLMClient.ACCEPTED_API_KEY));
        assertDoesNotThrow(() -> { createLLM(config, createGenerator(), secrets).complete(prompt, inferenceParams()); });
    }

    private static String lookupParameter(String parameter, Map<String, String> params) {
        return params.get(parameter);
    }

    private static InferenceParameters inferenceParams() {
        return new InferenceParameters(s -> lookupParameter(s, Collections.emptyMap()));
    }

    private static InferenceParameters inferenceParams(Map<String, String> params) {
        return new InferenceParameters(MockLLMClient.ACCEPTED_API_KEY, s -> lookupParameter(s, params));
    }

    private static InferenceParameters inferenceParamsWithDefaultKey() {
        return new InferenceParameters(MockLLMClient.ACCEPTED_API_KEY, s -> lookupParameter(s, Collections.emptyMap()));
    }

    private LlmClientConfig modelParams(String apiKeySecretName, String endpoint) {
        var config = new LlmClientConfig.Builder();
        if (apiKeySecretName != null) {
            config.apiKeySecretName(apiKeySecretName);
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

    private static MockLLMClient createLLM() {
        LlmClientConfig config = new LlmClientConfig.Builder().build();
        return createLLM(config, null);
    }

    private static MockLLMClient createLLM(ExecutorService executor) {
        LlmClientConfig config = new LlmClientConfig.Builder().build();
        return createLLM(config, executor);
    }

    private static MockLLMClient createLLM(LlmClientConfig config, ExecutorService executor) {
        var generator = createGenerator();
        var secretStore = new SecretStoreProvider();  // throws exception on use
        return createLLM(config, generator, secretStore.get(), executor);
    }

    private static MockLLMClient createLLM(LlmClientConfig config,
                                           BiFunction<Prompt, InferenceParameters, String> generator,
                                           SecretStore secretStore) {
        return createLLM(config, generator, secretStore, null);
    }

    private static MockLLMClient createLLM(LlmClientConfig config,
                                           BiFunction<Prompt, InferenceParameters, String> generator,
                                           SecretStore secretStore,
                                           ExecutorService executor) {
        return new MockLLMClient(config, secretStore, generator, executor);
    }

}
