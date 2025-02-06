// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.config.ModelReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test quality for LocalLLM.
 *
 * @author glebashnik
 */
public class LocalLLMQualityTest {
    private record PromptAnswer(String prompt, String answer) {}
    
    
    private static List<PromptAnswer> PROMPT_ANSWERS = List.of(
            new PromptAnswer(
                    "Translate this text to Norwegian." +
                            "The output should only include translated text. " +
                            "No notes or any other information. " +
                            "\nText:\n" +
                            "The presence of communication amid scientific minds was equally important " +
                            "to the success of the Manhattan Project as scientific intellect was." +
                            "The only cloud hanging over the impressive achievement of the atomic researchers and engineers " +
                            "is what their success truly meant; hundreds of thousands of innocent lives obliterated." +
                            "\nOutput:\n",
                    "Den tilstedeværelsen av kommunikasjon mellom videnskapsmenn var likså viktig " +
                            "for Manhattan-prosjektets succes som videnskapelig intelligens. " +
                            "Det eneste skyet over den indtrykkelige leggingen av atombrukere og ingeniører " +
                            "er hva deres succes verktatt betydet; hundrevis av tusenne uskyldige liv utryddet."
            ),
            new PromptAnswer(
                    "Translate this text to Norwegian." +
                            "The output should only include translated text." +
                            "No notes or any other information." +
                            "Text:" +
                            "Monitor your pulse. You should know how to take your pulse – " +
                            "especially if you have an artificial pacemaker. " +
                            "1.  Put the second and third fingers of one hand on the inside of the wrist of the other hand, " +
                            "just below the thumb OR on the side of your neck, just below the corner of your jaw. " +
                            "Feel for the pulse.",
                    "Kontroller hjertetslaget. " +
                            "Du bør vite hvordan å ta hjertetslag – særlig hvis du har en kunstig pacemaker. " +
                            "1. Legg tommefingeren og peifferen av en hånd på innersiden av hånden på den andre hånd, " +
                            "litt under tromfen OR på siden av halsen, litt under hjørnet av kjæken. " +
                            "Følge hjertetslaget."
            ),
            new PromptAnswer(
                    "Your tasks is to extract people names, project names, organization names, product names and location names from the input text." +
                            "The output must be a list of names separated by | or an empty string if no names were found. " +
                            "Nothing else should be included in the output. " +
                            "Here is the input text: " +
                            "Good morning everyone, this is going to be a short post about the retention offers " +
                            "I received on my 5 Citi credit cards. Before I go on, " +
                            "I highly recommend reading Doctor of Credit’s post on " +
                            "Retention Bonus Rules & Tips For Each Card Issuer – Get More Than One Bonus Each Year. " +
                            "I called Citi yesterday to tell them I was going to make a large “purchase” at Target " +
                            "with my Citi American Airlines Executive Credit Card.",
                    "Citi|Citi American Airlines Executive Credit Card|Target|Doctor of Credit"
            ),
            new PromptAnswer(
                    "Your tasks is to extract people names, project names, organization names, product names and location names from the input text." +
                            "The output must be a list of names separated by | or an empty string if no names were found. " +
                            "Nothing else should be included in the output. " +
                            "Here is the input text: " +
                            "David Hecker, President, AFT Michigan. Keith Johnson, President, " +
                            "Detroit Federation of Teachers. John McDonald, President, " +
                            "Henry Ford Community College Fed of Teachers. Ruby Newbold, President, " +
                            "Detroit Assoc. of Educational Office Employees.",
                    "David Hecker|AFT Michigan|President Keith Johnson|Detroit Federation of Teachers|" +
                            "President John McDonald|Henry Ford Community College Fed of Teachers|" +
                            "President Ruby Newbold|Detroit Assoc. of Educational Office Employees|President"
                    ),
            new PromptAnswer(
                    "Your tasks is to extract people names, project names, organization names, product names and location names from the input text." +
                            "The output must be a list of names separated by | or an empty string if no names were found. " +
                            "Nothing else should be included in the output. " +
                            "Here is the input text: " +
                            "The city quickly grew in prominence and became a bustling trading hub in the following centuries.",
                    ""
            )
    );
    

    @Test
    @Disabled
    public void testGenerationSequential() throws IOException {
        var llm = loadLLM(1, 4096);
        
        for (var promptAnswer : PROMPT_ANSWERS.subList(0, 5)) {
            assertAnswer(llm, promptAnswer.prompt(), promptAnswer.answer());
        }
    }
    
    @Test
    @Disabled
    public void testGenerationParallel() throws IOException {
        var llm = loadLLM(5, 4096);
        
        int cpuCount = Runtime.getRuntime().availableProcessors();
        var executor = Executors.newFixedThreadPool(cpuCount * 2);
        
        var futures = new ArrayList<CompletableFuture<Void>>();
        for (var promptAnswer : PROMPT_ANSWERS) {
            var future = CompletableFuture.runAsync(() -> assertAnswer(llm, promptAnswer.prompt(), promptAnswer.answer()), executor);
            futures.add(future);
        }
        
        for (var future : futures) {
            future.join();
        }
        
        executor.shutdown();
    }
    

    private LocalLLM loadLLM(int parallelRequests, int contextSizePerRequest) {
        var modelPath = "/Users/gleb/.cache/lm-studio/models/lmstudio-community/Mistral-7B-Instruct-v0.3-GGUF/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf";
        
        var config = new LlmLocalClientConfig.Builder()
                .parallelRequests(parallelRequests)
                .contextSize(parallelRequests * contextSizePerRequest)
                .model(ModelReference.valueOf(modelPath));

        return new LocalLLM(config.build());
    }

    public void assertAnswer(LocalLLM llm, String prompt, String expectedAnswer) {
        prompt = prompt.replaceAll("\\s+", " ").trim();
        expectedAnswer = expectedAnswer.replaceAll("\\s+", " ").trim();

        System.out.println(prompt);
        System.out.println(expectedAnswer);

        var result = llm.complete(StringPrompt.from(prompt), defaultOptions());
        var actualAnswer = result.get(0).text();
        actualAnswer = actualAnswer.replaceAll("\\s+", " ").trim();
        System.out.println(actualAnswer);

        if (!Completion.FinishReason.stop.equals(result.get(0).finishReason()) || !expectedAnswer.equals(actualAnswer)) {
            System.err.println("Warning: Expected answer:\n" + expectedAnswer + "\nActual answer:\n" + actualAnswer);
        }
    }

    private static InferenceParameters defaultOptions() {
        final Map<String, String> options = Map.of(
                "temperature", "0",
                "npredict", "500"
        );
        return new InferenceParameters(options::get);
    }

}
