// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LlmClientConfig;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.container.jdisc.SecretStoreProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class OpenAITest {

    private static final String apiKey = "<your-api-key>";

    @Test
    @Disabled
    public void testOpenAIGeneration() {
        var config = new LlmClientConfig.Builder().build();
        var openai = new OpenAI(config, new SecretStoreProvider().get());
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

}
