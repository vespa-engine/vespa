// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.StringPrompt;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

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
        System.out.print(completions.get(0).text());
        assertCompletionSize(completions.get(0), 3, 10);
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

        var future = openai.completeAsync(prompt, new InferenceParameters(API_KEY, options::get), completion -> {
            System.out.print(completion.text());
            //assertCompletionSize(completion, 3, 10);
        }).exceptionally(exception -> {
            System.out.println("Error: " + exception);
            return null;
        });
        future.join();
    }
    
    private void assertCompletionSize(Completion completion, int minTokens, int maxTokens) {
        // Splitting by space is a poor tokenizer but it is good enough for this test.
        var numTokens = completion.text().split(" ").length;
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
