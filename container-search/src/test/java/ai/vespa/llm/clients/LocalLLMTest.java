// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LocalLLM. Tests are disabled due to size of models.
 *
 * @author lesters
 */
public class LocalLLMTest {

    // Download a model - for instance https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q6_K.gguf
    private static String model = "path/to/model";

    @Test
    @Disabled
    public void testGeneration() {
        var prompt = StringPrompt.from("Why are ducks better than cats? Be concise, " +
                "but use the word 'spoon' somewhere in your answer.");
        var llm = createLLM(model);
        var result = llm.complete(prompt, defaultOptions());
        assertEquals(Completion.FinishReason.stop, result.get(0).finishReason());
        assertTrue(result.get(0).text().contains("spoon"));
        llm.deconstruct();
    }

    @Test
    @Disabled
    public void testAsyncGeneration() {
        var sb = new StringBuilder();
        Prompt prompt = StringPrompt.from(testContext() + "What was the Manhattan Project? Only use the documents given here as reference.");

        var config = new LlmLocalClientConfig.Builder()
                .useGpu(true)
                .parallelRequests(1)
                .contextSize(1024)
                .localLlmFile(model);
        var llm = new LocalLLM(config.build());

        var future = llm.completeAsync(prompt, defaultOptions(), completion -> {
            sb.append(completion.text());
            System.out.print(completion.text());
        }).exceptionally(exception -> Completion.FinishReason.error);

        assertFalse(future.isDone());
        var reason = future.join();
        assertTrue(future.isDone());
        assertNotEquals(reason, Completion.FinishReason.error);

        System.out.println(prompt.asString());
        System.out.println(sb);

        llm.deconstruct();
    }

    @Test
    @Disabled
    public void testParallelGeneration() {
        var prompts = testPrompts();
        var promptsToUse = prompts.size();
        var parallelRequests = 10;

        var futures = new ArrayList<CompletableFuture<Completion.FinishReason>>(Collections.nCopies(promptsToUse, null));
        var completions = new ArrayList<StringBuilder>(Collections.nCopies(promptsToUse, null));

        var config = new LlmLocalClientConfig.Builder()
                .useGpu(true)
                .parallelRequests(parallelRequests)
                .localLlmFile(model);
        var llm = new LocalLLM(config.build());

        var start = System.currentTimeMillis();
        for (int i = 0; i < promptsToUse; i++) {
            final var seq = i;

            completions.set(seq, new StringBuilder());
            futures.set(seq, llm.completeAsync(StringPrompt.from(prompts.get(seq)), defaultOptions(), completion -> {
                completions.get(seq).append(completion.text());
            }).exceptionally(exception -> Completion.FinishReason.error));
        }
        for (var future : futures) {
            var reason = future.join();
            assertNotEquals(reason, Completion.FinishReason.error);
        }
        for (int i = 0; i < promptsToUse; i++) {
            var reason = futures.get(i).join();
            assertNotEquals(reason, Completion.FinishReason.error);
            System.out.println("\n\n***\n" + prompts.get(i) + ":\n***\n" + completions.get(i));
        }
        System.out.println("Time: " + (System.currentTimeMillis() - start) / 1000.0 + "s");

        llm.deconstruct();
    }

    @Test
    @Disabled
    public void testRejection() {
        var prompts = testPrompts();
        var promptsToUse = prompts.size();
        var parallelRequests = 1;
        var additionalQueue = 0;
        // 9 should be rejected

        var futures = new ArrayList<CompletableFuture<Completion.FinishReason>>(Collections.nCopies(promptsToUse, null));
        var completions = new ArrayList<StringBuilder>(Collections.nCopies(promptsToUse, null));

        var config = new LlmLocalClientConfig.Builder()
                .useGpu(true)
                .parallelRequests(parallelRequests)
                .maxQueueSize(additionalQueue)
                .localLlmFile(model);
        var llm = new LocalLLM(config.build());

        final AtomicInteger rejected = new AtomicInteger(0);
        for (int i = 0; i < promptsToUse; i++) {
            final var seq = i;

            completions.set(seq, new StringBuilder());
            var future = llm.completeAsync(StringPrompt.from(prompts.get(seq)), defaultOptions(), completion -> {
                completions.get(seq).append(completion.text());
                if (completion.finishReason() == Completion.FinishReason.error) {
                    rejected.incrementAndGet();
                }
            }).exceptionally(exception -> Completion.FinishReason.error);
            futures.set(seq, future);
        }
        for (int i = 0; i < promptsToUse; i++) {
            futures.get(i).join();
            System.out.println("\n\n***\n" + prompts.get(i) + ":\n***\n" + completions.get(i));
        }

        assertEquals(9, rejected.get());
        llm.deconstruct();
    }

    private static InferenceParameters defaultOptions() {
        final Map<String, String> options = Map.of(
                "temperature", "0.1",
                "npredict", "100"
        );
        return new InferenceParameters(options::get);
    }

    private List<String> testPrompts() {
        List<String> prompts = new ArrayList<>();
        prompts.add("Write a short story about a time-traveling detective who must solve a mystery that spans multiple centuries.");
        prompts.add("Explain the concept of blockchain technology and its implications for data security in layman's terms.");
        prompts.add("Discuss the socio-economic impacts of the Industrial Revolution in 19th century Europe.");
        prompts.add("Describe a future where humans have colonized Mars, focusing on daily life and societal structure.");
        prompts.add("Analyze the statement 'If a tree falls in a forest and no one is around to hear it, does it make a sound?' from both a philosophical and a physics perspective.");
        prompts.add("Translate the following sentence into French: 'The quick brown fox jumps over the lazy dog.'");
        prompts.add("Explain what the following Python code does: `print([x for x in range(10) if x % 2 == 0])`.");
        prompts.add("Provide general guidelines for maintaining a healthy lifestyle to reduce the risk of developing heart disease.");
        prompts.add("Create a detailed description of a fictional planet, including its ecosystem, dominant species, and technology level.");
        prompts.add("Discuss the impact of social media on interpersonal communication in the 21st century.");
        return prompts;
    }

    private static String testContext() {
        return "sddocname: passage\n" +
               "id: 2\n" +
               "text: Essay on The Manhattan Project - The Manhattan Project The Manhattan Project was to see if making an atomic bomb possible. The success of this project would forever change the world forever making it known that something this powerful can be manmade.\n" +
               "documentid: id:msmarco:passage::2\n" +
               "\n" +
               "sddocname: passage\n" +
               "id: 0\n" +
               "text: The presence of communication amid scientific minds was equally important to the success of the Manhattan Project as scientific intellect was. The only cloud hanging over the impressive achievement of the atomic researchers and engineers is what their success truly meant; hundreds of thousands of innocent lives obliterated.\n" +
               "documentid: id:msmarco:passage::0\n" +
               "\n" +
               "sddocname: passage\n" +
               "id: 7\n" +
               "text: Manhattan Project. The Manhattan Project was a research and development undertaking during World War II that produced the first nuclear weapons. It was led by the United States with the support of the United Kingdom and Canada. From 1942 to 1946, the project was under the direction of Major General Leslie Groves of the U.S. Army Corps of Engineers. Nuclear physicist Robert Oppenheimer was the director of the Los Alamos Laboratory that designed the actual bombs. The Army component of the project was designated the\n" +
               "documentid: id:msmarco:passage::7\n" +
               "\n" +
               "sddocname: passage\n" +
               "id: 3\n" +
               "text: The Manhattan Project was the name for a project conducted during World War II, to develop the first atomic bomb. It refers specifically to the period of the project from 194 … 2-1946 under the control of the U.S. Army Corps of Engineers, under the administration of General Leslie R. Groves.\n" +
               "documentid: id:msmarco:passage::3\n" +
               "\n" +
               "sddocname: passage\n" +
               "id: 9\n" +
               "text: One of the main reasons Hanford was selected as a site for the Manhattan Project's B Reactor was its proximity to the Columbia River, the largest river flowing into the Pacific Ocean from the North American coast.\n" +
               "documentid: id:msmarco:passage::9\n" +
               "\n" +
               "sddocname: passage\n" +
               "id: 5\n" +
               "text: The Manhattan Project. This once classified photograph features the first atomic bomb — a weapon that atomic scientists had nicknamed Gadget.. The nuclear age began on July 16, 1945, when it was detonated in the New Mexico desert.\n" +
               "documentid: id:msmarco:passage::5\n" +
               "\n" +
               "sddocname: passage\n" +
               "id: 8\n" +
               "text: In June 1942, the United States Army Corps of Engineers began the Manhattan Project- The secret name for the 2 atomic bombs.\n" +
               "documentid: id:msmarco:passage::8\n" +
               "\n" +
               "sddocname: passage\n" +
               "id: 1\n" +
               "text: The Manhattan Project and its atomic bomb helped bring an end to World War II. Its legacy of peaceful uses of atomic energy continues to have an impact on history and science.\n" +
               "documentid: id:msmarco:passage::1\n" +
               "\n" +
               "sddocname: passage\n" +
               "id: 6\n" +
               "text: Nor will it attempt to substitute for the extraordinarily rich literature on the atomic bombs and the end of World War II. This collection does not attempt to document the origins and development of the Manhattan Project.\n" +
               "documentid: id:msmarco:passage::6\n" +
               "\n" +
               "sddocname: passage\n" +
               "id: 4\n" +
               "text: versions of each volume as well as complementary websites. The first website–The Manhattan Project: An Interactive History–is available on the Office of History and Heritage Resources website, http://www.cfo. doe.gov/me70/history. The Office of History and Heritage Resources and the National Nuclear Security\n" +
               "documentid: id:msmarco:passage::4\n" +
               "\n" +
               "\n";
    }

    private static LocalLLM createLLM(String modelPath) {
        var config = new LlmLocalClientConfig.Builder().localLlmFile(modelPath).build();
        return new LocalLLM(config);
    }

}
