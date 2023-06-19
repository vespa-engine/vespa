package ai.vespa.llm.completion;

import ai.vespa.llm.test.MockLanguageModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests completion with a mock completer.
 *
 * @author bratseth
 */
public class CompletionTest {

    @Test
    public void testCompletion() {
        Function<Prompt, List<Completion>> completer = in ->
            switch (in.asString()) {
                case "Complete this: " -> List.of(Completion.from("The completion"));
                default -> throw new RuntimeException("Cannot complete '" + in + "'");
        };
        var llm = new MockLanguageModel.Builder().completer(completer).build();

        String input = "Complete this: ";
        StringPrompt prompt = StringPrompt.from(input);
        for (int i = 0; i < 10; i++) {
            var completion = llm.complete(prompt).get(0);
            prompt = prompt.append(completion);
            if (completion.finishReason() == Completion.FinishReason.stop) break;
        }
        assertEquals("Complete this: The completion", prompt.asString());
    }

}
