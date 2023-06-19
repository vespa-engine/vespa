package ai.vespa.llm.client.openai;

import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.StringPrompt;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author bratseth
 */
public class OpenAiClientCompletionTest {

    @Test
    @Disabled
    public void testClient() {
        var client = new OpenAiClient.Builder("your token here").build();
        String input = "You are an unhelpful assistant who never answers questions straightforwardly. " +
                       "Be as long-winded as possible. Are humans smarter than cats?";
        StringPrompt prompt = StringPrompt.from(input);
        System.out.print(prompt);
        for (int i = 0; i < 10; i++) {
            var completion = client.complete(prompt).get(0);
            System.out.print(completion.text());
            if (completion.finishReason() == Completion.FinishReason.stop) break;
            prompt = prompt.append(completion.text());
        }
    }

}
