// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.container.jdisc.SecretsProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;

public class OpenAITest {

    private static final String apiKey = "<your-api-key>";

    @Test
    @Disabled
    public void testOpenAIGeneration() {
        var config = new LlmClientConfig.Builder().build();
        var openai = new OpenAI(config, new SecretsProvider().get());
        var options = Map.of(
                "maxTokens", "10"
        );

        var prompt = StringPrompt.from("why are ducks better than cats?");
        var future = openai.completeAsync(prompt, new InferenceParameters(apiKey, options::get), completion -> {
            System.out.print(completion.text());
        }).exceptionally(exception -> {
            System.out.println("Error: " + exception);
            return null;
        });
        future.join();
    }

    @Test
    @Disabled
    public void testComplete() {
        var config = new LlmClientConfig.Builder().maxTokens(10).build();
        var openai = new OpenAI(config, new SecretsProvider().get());
        var options = Map.of(
                "model", "gpt-4o-mini"
        );
        var prompt = StringPrompt.from("Explain why ducks better than cats in 20 words?");
        var completions = openai.complete(prompt, new InferenceParameters(apiKey, options::get));
        assertFalse(completions.isEmpty());
        
        // Token is smaller than word. 
        // Splitting by space is a poor tokenizer but it is good enough for this test.
        assertTrue(completions.get(0).text().split(" ").length <= 10);
    }

}
