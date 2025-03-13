// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.StringPrompt;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class OpenAITest {

    private static final String API_KEY = "<YOUR_API_KEY>";
    
    @Test
    @Disabled
    public void testComplete() {
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("openai")
                .maxTokens(10)
                .build();
        var openai = new OpenAI(config, new MockSecrets());
        var options = Map.of(
                "model", "gpt-4o-mini"
        );
        var prompt = StringPrompt.from("Explain why ducks better than cats in 20 words?");
        var completions = openai.complete(prompt, new InferenceParameters(options::get));
        var text = completions.get(0).text();
        
        System.out.print(text);
        assertNumTokens(text, 3, 10);
    }

    @Test
    @Disabled
    public void testCompleteAsync() {
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("openai")
                .maxTokens(10)
                .build();
        var openai = new OpenAI(config, new MockSecrets());
        var options = Map.of(
                "model", "gpt-4o-mini"
        );
        var prompt = StringPrompt.from("Explain why ducks better than cats in 20 words?");
        var text = new StringBuilder();
        
        var future = openai.completeAsync(prompt, new InferenceParameters(API_KEY, options::get), completion -> {
            text.append(completion.text());
        }).exceptionally(exception -> {
            System.out.println("Error: " + exception);
            return null;
        });
        future.join();
        
        System.out.print(text);
        assertNumTokens(text.toString(), 3, 10);
    }
    
    @Test
    public void testClientCaching() throws Exception {
        // Create OpenAI instance
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("openai")
                .build();
        var openai = new OpenAI(config, new MockSecrets());
        
        // Access private fields via reflection
        Field defaultSyncClientField = OpenAI.class.getDeclaredField("defaultSyncClient");
        Field defaultAsyncClientField = OpenAI.class.getDeclaredField("defaultAsyncClient");
        Field cachedSyncApiKeyField = OpenAI.class.getDeclaredField("cachedSyncApiKey");
        Field cachedSyncEndpointField = OpenAI.class.getDeclaredField("cachedSyncEndpoint");
        Field cachedAsyncApiKeyField = OpenAI.class.getDeclaredField("cachedAsyncApiKey");
        Field cachedAsyncEndpointField = OpenAI.class.getDeclaredField("cachedAsyncEndpoint");
        
        defaultSyncClientField.setAccessible(true);
        defaultAsyncClientField.setAccessible(true);
        cachedSyncApiKeyField.setAccessible(true);
        cachedSyncEndpointField.setAccessible(true);
        cachedAsyncApiKeyField.setAccessible(true);
        cachedAsyncEndpointField.setAccessible(true);
        
        // Get the private client getter methods via reflection
        Method getSyncClientMethod = OpenAI.class.getDeclaredMethod("getSyncClient", String.class, String.class);
        Method getAsyncClientMethod = OpenAI.class.getDeclaredMethod("getAsyncClient", String.class, String.class);
        getSyncClientMethod.setAccessible(true);
        getAsyncClientMethod.setAccessible(true);
        
        // Initial state should be null
        assertNull(defaultSyncClientField.get(openai));
        assertNull(defaultAsyncClientField.get(openai));
        
        String testApiKey = "test-api-key";
        String testEndpoint = "https://api.openai.com/v1/";
        
        // First client creation
        Object syncClient1 = getSyncClientMethod.invoke(openai, testApiKey, testEndpoint);
        Object asyncClient1 = getAsyncClientMethod.invoke(openai, testApiKey, testEndpoint);
        
        // Verify clients were created and cached
        assertNotNull(syncClient1);
        assertNotNull(asyncClient1);
        assertEquals(testApiKey, cachedSyncApiKeyField.get(openai));
        assertEquals(testEndpoint, cachedSyncEndpointField.get(openai));
        assertEquals(testApiKey, cachedAsyncApiKeyField.get(openai));
        assertEquals(testEndpoint, cachedAsyncEndpointField.get(openai));
        assertSame(syncClient1, defaultSyncClientField.get(openai));
        assertSame(asyncClient1, defaultAsyncClientField.get(openai));
        
        // Same parameters should return the same clients
        Object syncClient2 = getSyncClientMethod.invoke(openai, testApiKey, testEndpoint);
        Object asyncClient2 = getAsyncClientMethod.invoke(openai, testApiKey, testEndpoint);
        assertSame(syncClient1, syncClient2);
        assertSame(asyncClient1, asyncClient2);
        
        // Different parameters should create new clients
        String differentApiKey = "different-api-key";
        Object syncClient3 = getSyncClientMethod.invoke(openai, differentApiKey, testEndpoint);
        Object asyncClient3 = getAsyncClientMethod.invoke(openai, differentApiKey, testEndpoint);
        assertNotSame(syncClient1, syncClient3);
        assertNotSame(asyncClient1, asyncClient3);
        
        // Cached values should be updated
        assertEquals(differentApiKey, cachedSyncApiKeyField.get(openai));
        assertEquals(testEndpoint, cachedSyncEndpointField.get(openai));
        assertEquals(differentApiKey, cachedAsyncApiKeyField.get(openai));
        assertEquals(testEndpoint, cachedAsyncEndpointField.get(openai));
        
        // Different endpoint should also create new clients
        String differentEndpoint = "https://different-endpoint.com/v1/";
        Object syncClient4 = getSyncClientMethod.invoke(openai, differentApiKey, differentEndpoint);
        Object asyncClient4 = getAsyncClientMethod.invoke(openai, differentApiKey, differentEndpoint);
        assertNotSame(syncClient3, syncClient4);
        assertNotSame(asyncClient3, asyncClient4);
        
        // Using original parameters again should create new clients
        // since the cache now has different values
        Object syncClient5 = getSyncClientMethod.invoke(openai, testApiKey, testEndpoint);
        Object asyncClient5 = getAsyncClientMethod.invoke(openai, testApiKey, testEndpoint);
        assertNotSame(syncClient1, syncClient5);
        assertNotSame(asyncClient1, asyncClient5);
    }
    
    private void assertNumTokens(String completion, int minTokens, int maxTokens) {
        // Splitting by space is a poor tokenizer but it is good enough for this test.
        var numTokens = completion.split(" ").length;
        assertTrue( minTokens <= numTokens && numTokens <= maxTokens);
    }
    
    static class MockSecrets implements Secrets {
        @Override
        public Secret get(String key) {
            if (key.equals("openai")) {
                return new Secret() {
                    @Override
                    public String current() {
                        return API_KEY;
                    }
                };
            }
            
            return null;
        }
    }

}
