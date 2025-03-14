// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.StringPrompt;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

public class OpenAICompatibleTest {
    // API key for the Together API
    private static final String API_KEY = "<your-api-key>";
    private static final String TOGETHER_ENDPOINT = "https://api.together.xyz/v1";
    private static final String API_KEY_NAME = "together-apikey";
    
    // List of models to test
    private static final List<String> TEST_MODELS = List.of(
            "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo",
            "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B"
    );

    @Test
    @Disabled
    public void testComplete() {
        for (String model : TEST_MODELS) {
            System.out.println("Testing model: " + model);
            
            var config = new LlmClientConfig.Builder()
                    .endpoint(TOGETHER_ENDPOINT)
                    .apiKeySecretName(API_KEY_NAME)
                    .maxTokens(300)
                    .build();
            var openai = new OpenAI(config, new MockSecrets());
            var options = Map.of("model", model);
            var prompt = StringPrompt.from("Write a haiku about the most beautiful place on Earth.");
            var completions = openai.complete(prompt, new InferenceParameters(options::get));
            var text = completions.get(0).text();
            // Reasoning models do not count thinking tokens to the maxTokens, so may return a longer completion.
            assertNumTokens(text, 3, 3000);
            
            System.out.println("Result: " + text);
            System.out.println("----------------------------------------");
        }
    }

    @Test
    @Disabled
    public void testCompleteAsync() {
        for (String model : TEST_MODELS) {
            System.out.println("Testing async model: " + model);
            
            var config = new LlmClientConfig.Builder()
                    .endpoint(TOGETHER_ENDPOINT)
                    .apiKeySecretName(API_KEY_NAME)
                    .maxTokens(300)
                    .build();
            var openai = new OpenAI(config, new MockSecrets());
            var options = Map.of("model", model);
            var prompt = StringPrompt.from("Write a haiku about the most beautiful place on Earth.");
            var text = new StringBuilder();
            
            var future = openai.completeAsync(prompt, new InferenceParameters(options::get), completion -> {
                text.append(completion.text());
            });

            future.join();
            assertNumTokens(text.toString(), 3, 3000);
            
            System.out.println("Async result: " + text.toString());
            System.out.println("----------------------------------------");
        }
    }
    
    private void assertNumTokens(String completion, int minTokens, int maxTokens) {
        // Splitting by space is a poor tokenizer but it is good enough for this test.
        var numTokens = completion.split(" ").length;
        assertTrue(minTokens <= numTokens && numTokens <= maxTokens);
    }
    
    static class MockSecrets implements Secrets {
        @Override
        public Secret get(String key) {
            if (key.equals(API_KEY_NAME)) {
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