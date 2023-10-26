// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.client.openai;

import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.StringPrompt;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author bratseth
 */
public class OpenAiClientCompletionTest {

    private static final String apiKey = "your-api-key-here";

    @Test
    @Disabled
    public void testClient() {
        var client = new OpenAiClient.Builder(apiKey).maxTokens(10).build();
        String input = "You are an unhelpful assistant who never answers questions straightforwardly. " +
                "Be as long-winded as possible. Are humans smarter than cats?\n\n";
        StringPrompt prompt = StringPrompt.from(input);
        System.out.print(prompt);
        for (int i = 0; i < 10; i++) {
            var completion = client.complete(prompt).get(0);
            System.out.print(completion.text());
            if (completion.finishReason() == Completion.FinishReason.stop) break;
            prompt = prompt.append(completion.text());
        }
    }

    @Test
    @Disabled
    public void testAsyncClient() {
        var client = new OpenAiClient.Builder(apiKey).build();
        String input = "You are an unhelpful assistant who never answers questions straightforwardly. " +
                "Be as long-winded as possible. Are humans smarter than cats?\n\n";
        StringPrompt prompt = StringPrompt.from(input);
        System.out.print(prompt);
        var future = client.completeAsync(prompt, completion -> {
            System.out.print(completion.text());
        });
        System.out.println("Waiting for completion...");
        System.out.println("\nFinished streaming because of " + future.join());
    }

}
