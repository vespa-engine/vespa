// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.StringPrompt;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import org.junit.jupiter.api.Test;
import com.openai.models.ReasoningEffort;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrcaRouterTest {

    private static final String API_KEY = "<YOUR_API_KEY>";

    @Test
    public void testReasoningEffortOption() {
        var prompt = StringPrompt.from("hello");

        // Not set: not sent to the API
        var orcaRouter = new OrcaRouter(new LlmClientConfig.Builder().apiKeySecretName("orcarouter").build(), new MockSecrets());
        var params = orcaRouter.getChatCompletionCreateParams(new InferenceParameters(Map.<String, String>of()::get), prompt);
        assertTrue(params.reasoningEffort().isEmpty());

        // Set in component config
        var orcaRouterWithConfig = new OrcaRouter(
                new LlmClientConfig.Builder().apiKeySecretName("orcarouter").reasoningEffort("low").build(),
                new MockSecrets());
        params = orcaRouterWithConfig.getChatCompletionCreateParams(
                orcaRouterWithConfig.prepareParameters(new InferenceParameters(Map.<String, String>of()::get)), prompt);
        assertEquals(ReasoningEffort.LOW, params.reasoningEffort().orElseThrow());
    }

    @Test
    public void testDefaultModel() {
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("orcarouter")
                .build();
        var orcaRouter = new OrcaRouter(config, new MockSecrets());
        var prompt = StringPrompt.from("hello");

        var params = orcaRouter.prepareParameters(new InferenceParameters(Map.<String, String>of()::get));
        var createParams = orcaRouter.getChatCompletionCreateParams(params, prompt);

        assertEquals("orcarouter/fusion-mini", createParams.model().toString());
    }

    @Test
    public void testConfiguredModel() {
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("orcarouter")
                .model("deepseek/deepseek-chat")
                .build();
        var orcaRouter = new OrcaRouter(config, new MockSecrets());
        var prompt = StringPrompt.from("hello");

        var params = orcaRouter.prepareParameters(new InferenceParameters(Map.<String, String>of()::get));
        var createParams = orcaRouter.getChatCompletionCreateParams(params, prompt);

        assertEquals("deepseek/deepseek-chat", createParams.model().toString());
    }

    @Test
    public void testClientCaching() {
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("orcarouter")
                .build();
        var orcaRouter = new OrcaRouter(config, new MockSecrets());

        assertNull(orcaRouter.defaultSyncClient);
        assertNull(orcaRouter.defaultAsyncClient);

        String testApiKey = "test-api-key";
        String testEndpoint = "https://api.orcarouter.ai/v1/";

        var syncClient1 = orcaRouter.getSyncClient(testApiKey, testEndpoint);
        var asyncClient1 = orcaRouter.getAsyncClient(testApiKey, testEndpoint);

        assertNotNull(syncClient1);
        assertNotNull(asyncClient1);
        assertEquals(testApiKey, orcaRouter.cachedSyncApiKey);
        assertEquals(testEndpoint, orcaRouter.cachedSyncEndpoint);
        assertSame(syncClient1, orcaRouter.defaultSyncClient);
        assertSame(asyncClient1, orcaRouter.defaultAsyncClient);

        // Same parameters should return the same clients
        var syncClient2 = orcaRouter.getSyncClient(testApiKey, testEndpoint);
        var asyncClient2 = orcaRouter.getAsyncClient(testApiKey, testEndpoint);
        assertSame(syncClient1, syncClient2);
        assertSame(asyncClient1, asyncClient2);

        // Different parameters should create new clients
        String differentApiKey = "different-api-key";
        var syncClient3 = orcaRouter.getSyncClient(differentApiKey, testEndpoint);
        var asyncClient3 = orcaRouter.getAsyncClient(differentApiKey, testEndpoint);
        assertNotSame(syncClient1, syncClient3);
        assertNotSame(asyncClient1, asyncClient3);
    }

    static class MockSecrets implements Secrets {
        private final String apiKeyValue;

        MockSecrets() {
            this(API_KEY);
        }

        MockSecrets(String apiKeyValue) {
            this.apiKeyValue = apiKeyValue;
        }

        @Override
        public Secret get(String key) {
            if (key.equals("orcarouter")) {
                return new Secret() {
                    @Override
                    public String current() {
                        return apiKeyValue;
                    }
                };
            }
            return null;
        }
    }
}
