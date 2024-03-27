// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.client.openai;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.StringPrompt;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author bratseth
 */
public class OpenAiClientCompletionTest {

    private static final String apiKey = "<your-api-key-here>";

    @Test
    @Disabled
    public void testClient() {
        var client = new OpenAiClient();
        var options = Map.of("maxTokens", "10");
        var prompt = StringPrompt.from("You are an unhelpful assistant who never answers questions straightforwardly. " +
                "Be as long-winded as possible. Are humans smarter than cats?");

        System.out.print(prompt);
        var completion = client.complete(prompt, new InferenceParameters(apiKey, options::get)).get(0);
        System.out.print(completion.text());
    }

    @Test
    @Disabled
    public void testAsyncClient() {
        var client = new OpenAiClient();
        var options = Map.of("maxTokens", "10");
        var prompt = StringPrompt.from("You are an unhelpful assistant who never answers questions straightforwardly. " +
                "Be as long-winded as possible. Are humans smarter than cats?");
        System.out.print(prompt);
        var future = client.completeAsync(prompt, new InferenceParameters(apiKey, options::get), completion -> {
            System.out.print(completion.text());
        });
        System.out.println("\nWaiting for completion...\n\n");
        System.out.println("\nFinished streaming because of " + future.join());
    }

}
