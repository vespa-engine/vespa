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

public class DeepSeekTest {

    private static final String API_KEY = "<TOGETHER_API_KEY>";
    private static final String DEEPSEEK_ENDPOINT = "https://api.together.xyz/v1/chat/completions";

    @Test
    @Disabled
    public void testComplete() {
        var config = new LlmClientConfig.Builder()
                .endpoint(DEEPSEEK_ENDPOINT)
                .apiKeySecretName("deepseek-apikey")
                .maxTokens(300)
                .build();
        var openai = new OpenAI(config, new MockSecrets());
        var options = Map.of(
                "model", "deepseek-ai/DeepSeek-R1"
        );
        var prompt = StringPrompt.from("Explain why ducks better than cats in 20 words?");
        var completions = openai.complete(prompt, new InferenceParameters(options::get));
        var text = completions.get(0).text();
        assertNumTokens(text, 3, 200);
    }

    @Test
    @Disabled
    public void testCompleteAsync() {
        var config = new LlmClientConfig.Builder()
                .endpoint(DEEPSEEK_ENDPOINT)
                .apiKeySecretName("deepseek-apikey")
                .maxTokens(300)
                .build();
        var openai = new OpenAI(config, new MockSecrets());
        var options = Map.of(
                "model", "deepseek-ai/DeepSeek-R1"
        );
        var prompt = StringPrompt.from("Explain how ducks are better than cats. Do it in less than 20 words?");
        var text = new StringBuilder();
        
        var future = openai.completeAsync(prompt, new InferenceParameters(API_KEY, options::get), completion -> {
            text.append(completion.text());
        });

        future.join();
        assertNumTokens(text.toString(), 3, 300);
    }
    
    private void assertNumTokens(String completion, int minTokens, int maxTokens) {
        // Splitting by space is a poor tokenizer but it is good enough for this test.
        var numTokens = completion.split(" ").length;
        assertTrue( minTokens <= numTokens && numTokens <= maxTokens);
    }
    
    static class MockSecrets implements Secrets {
        @Override
        public Secret get(String key) {
            if (key.equals("deepseek-apikey")) {
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
