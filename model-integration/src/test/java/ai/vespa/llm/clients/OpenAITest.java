// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.StringPrompt;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.OpenAIIoException;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class OpenAITest {
    /*
     * This test will only work with the official OpenAI endpoint. Use {@link OpenAICompatibleTest} for other endpoints.
     */
    private static final String API_KEY = "<YOUR_API_KEY>";
    
    @Test
    //@Disabled
    public void testComplete() {
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("openai")
                .maxTokens(10)
                .build();
        var openai = new OpenAI(config, new MockSecrets());
        var prompt = StringPrompt.from("Explain why ducks better than cats in 20 words?");
        var completions = openai.complete(prompt, new InferenceParameters(API_KEY, key -> null));
        var text = completions.get(0).text();
        
        System.out.print(text);
        assertNumTokens(text, 3, 10);
    }

    @Test
    //@Disabled
    public void testCompleteAsync() {
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("openai")
                .maxTokens(10)
                .build();
        var openai = new OpenAI(config, new MockSecrets());
        var prompt = StringPrompt.from("Explain why ducks better than cats in 20 words?");
        var text = new StringBuilder();
        
        var future = openai.completeAsync(prompt, new InferenceParameters(API_KEY, key -> null), completion -> {
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
    public void testClientCaching() {
        // Create OpenAI instance
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("openai")
                .build();
        var openai = new OpenAI(config, new MockSecrets());
        
        // Initial state should be null
        assertNull(openai.defaultSyncClient);
        assertNull(openai.defaultAsyncClient);
        
        String testApiKey = "test-api-key";
        String testEndpoint = "https://api.openai.com/v1/";
        
        // First client creation
        var syncClient1 = openai.getSyncClient(testApiKey, testEndpoint);
        var asyncClient1 = openai.getAsyncClient(testApiKey, testEndpoint);
        
        // Verify clients were created and cached
        assertNotNull(syncClient1);
        assertNotNull(asyncClient1);
        assertEquals(testApiKey, openai.cachedSyncApiKey);
        assertEquals(testEndpoint, openai.cachedSyncEndpoint);
        assertEquals(testApiKey, openai.cachedAsyncApiKey);
        assertEquals(testEndpoint, openai.cachedAsyncEndpoint);
        assertSame(syncClient1, openai.defaultSyncClient);
        assertSame(asyncClient1, openai.defaultAsyncClient);
        
        // Same parameters should return the same clients
        var syncClient2 = openai.getSyncClient(testApiKey, testEndpoint);
        var asyncClient2 = openai.getAsyncClient(testApiKey, testEndpoint);
        assertSame(syncClient1, syncClient2);
        assertSame(asyncClient1, asyncClient2);
        
        // Different parameters should create new clients
        String differentApiKey = "different-api-key";
        var syncClient3 = openai.getSyncClient(differentApiKey, testEndpoint);
        var asyncClient3 = openai.getAsyncClient(differentApiKey, testEndpoint);
        assertNotSame(syncClient1, syncClient3);
        assertNotSame(asyncClient1, asyncClient3);
        
        // Cached values should be updated
        assertEquals(differentApiKey, openai.cachedSyncApiKey);
        assertEquals(testEndpoint, openai.cachedSyncEndpoint);
        assertEquals(differentApiKey, openai.cachedAsyncApiKey);
        assertEquals(testEndpoint, openai.cachedAsyncEndpoint);
        
        // Different endpoint should also create new clients
        String differentEndpoint = "https://different-endpoint.com/v1/";
        var syncClient4 = openai.getSyncClient(differentApiKey, differentEndpoint);
        var asyncClient4 = openai.getAsyncClient(differentApiKey, differentEndpoint);
        assertNotSame(syncClient3, syncClient4);
        assertNotSame(asyncClient3, asyncClient4);
        
        // Using original parameters again should create new clients
        // since the cache now has different values
        var syncClient5 = openai.getSyncClient(testApiKey, testEndpoint);
        var asyncClient5 = openai.getAsyncClient(testApiKey, testEndpoint);
        assertNotSame(syncClient1, syncClient5);
        assertNotSame(asyncClient1, asyncClient5);
    }

    @Test
    public void testCompleteMissingApiKey() {
        // Create OpenAI instance (will override the API key in the InferenceParameters)
        var config = new LlmClientConfig.Builder().build();
        var openai = new OpenAI(config, new MockSecrets());
        
        var prompt = StringPrompt.from("This should fail");
        // Override with null API key
        var parameters = new InferenceParameters(null, key -> null);
        
        // Verify the correct exception is thrown 
        org.junit.jupiter.api.Assertions.assertThrows(
                UnauthorizedException.class,
                () -> openai.complete(prompt, parameters)
        );
    }
    
    @Test
    public void testCompleteAsyncMissingApiKey() {
        // Create OpenAI instance with no API key in config
        var config = new LlmClientConfig.Builder().build();
        var openai = new OpenAI(config, new MockSecrets());
        
        var prompt = StringPrompt.from("This should fail");
        // No API key provided in parameters either
        var parameters = new InferenceParameters(null, key -> null);
        StringBuilder result = new StringBuilder();
        
        // Call completeAsync and get the future
        CompletableFuture<Completion.FinishReason> future = 
                openai.completeAsync(prompt, parameters, completion -> result.append(completion.text()));
        
        // Verify the future completed exceptionally
        // We need to check if the cause is UnauthorizedException, as CompletableFuture wraps exceptions
        CompletionException exception = org.junit.jupiter.api.Assertions.assertThrows(
            CompletionException.class,
                () -> future.join() // This will throw the wrapped exception
        );
        Throwable cause = exception.getCause();
        // Debug info in case of failure
        System.out.println("Exception class: " + exception.getClass().getName());
        if (exception.getCause() != null) {
            System.out.println("Cause class: " + exception.getCause().getClass().getName());
        }
        
        assertTrue(cause instanceof UnauthorizedException, 
                   "Expected UnauthorizedException but got: " + cause.getClass().getName());
    }

    @Test
    public void testInvalidApiKey() {
        // Create OpenAI instance with custom API key
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("openai")
                .build();
        
        var openai = new OpenAI(config, new MockSecrets("INVALID_API_KEY"));
        
        // Prepare a prompt and parameters with the default endpoint
        var prompt = StringPrompt.from("This should fail");
        var parameters = new InferenceParameters(key -> null);
        
        UnauthorizedException exception = org.junit.jupiter.api.Assertions.assertThrows(
                UnauthorizedException.class,
                () -> openai.complete(prompt, parameters)
        );
        
        // Verify the exception message contains information about the invalid API key
        assertTrue(exception.getMessage().contains("Incorrect API key provided"));
        assertEquals(401, exception.statusCode());
    }
    
    @Test
    public void testInvalidEndpoint() {
        // Create OpenAI instance with valid API key but we'll override the endpoint
        var config = new LlmClientConfig.Builder()
                .apiKeySecretName("openai")
                .build();
        var openai = new OpenAI(config, new MockSecrets());
        
        // Prepare a prompt and parameters with an invalid endpoint
        var prompt = StringPrompt.from("This should fail");
        var endpoint = "https://api.invalid.com/v1/invalid";
        var parameters = new InferenceParameters(API_KEY, key -> null);
        parameters.setEndpoint(endpoint);
        
        // An exception should be thrown when attempting to use the invalid endpoint
        org.junit.jupiter.api.Assertions.assertThrows(
            OpenAIIoException.class,
                () -> openai.complete(prompt, parameters)
        );
    }
    
    private void assertNumTokens(String completion, int minTokens, int maxTokens) {
        // Splitting by space is a poor tokenizer but it is good enough for this test.
        var numTokens = completion.split(" ").length;
        assertTrue( minTokens <= numTokens && numTokens <= maxTokens);
    }
    
    static class MockSecrets implements Secrets {
        private final String apiKeyValue;
        
        // Default constructor uses the constant API_KEY
        MockSecrets() {
            this(API_KEY);
        }
        
        // Constructor that allows specifying a custom API key
        MockSecrets(String apiKeyValue) {
            this.apiKeyValue = apiKeyValue;
        }
        
        @Override
        public Secret get(String key) {
            if (key.equals("openai")) {
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
