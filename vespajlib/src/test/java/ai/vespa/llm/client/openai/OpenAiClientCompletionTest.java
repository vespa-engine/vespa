// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.client.openai;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.StringPrompt;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 * @author thomasht86
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
        System.out.print("Running sync completion for: ");
        System.out.print(prompt);
        var completion = client.complete(prompt, new InferenceParameters(apiKey, options::get)).get(0);
        System.out.print(completion.text());
        System.out.println("\nFinished sync completion because of " + completion.finishReason());
        assertEquals("length", completion.finishReason().toString());
        assertTrue(completion.text().length() > 10, "Response should have more than 10 characters");
    }

    @Test
    @Disabled
    public void testAsyncClient() {
        var client = new OpenAiClient();
        var options = Map.of("maxTokens", "10");
        var prompt = StringPrompt.from("You are an unhelpful assistant who never answers questions straightforwardly. " +
                "Be as long-winded as possible. Are humans smarter than cats?");
        System.out.print("Running async completion for: ");
        System.out.print(prompt);
        StringBuilder responseText = new StringBuilder();
        var future = client.completeAsync(prompt, new InferenceParameters(apiKey, options::get), completion -> {
            String text = completion.text();
            responseText.append(text);
            System.out.print(text + "\n");
        });
        var finishReason = future.join().toString();
        System.out.println("\nFinished async streaming because of " + finishReason);
        assertEquals("length", finishReason);
        assertTrue(responseText.length() > 10, "Response should have more than 10 characters");
    }

}
