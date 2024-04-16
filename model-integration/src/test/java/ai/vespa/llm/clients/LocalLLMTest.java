// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.config.ModelReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LocalLLM.
 *
 * @author lesters
 */
public class LocalLLMTest {

    private static String model = "src/test/models/llm/tinyllm.gguf";
    private static Prompt prompt = StringPrompt.from("A random prompt");

    @Test
    @Disabled
    public void testGeneration() {
        var config = new LlmLocalClientConfig.Builder()
                .parallelRequests(1)
                .model(ModelReference.valueOf(model));
        var llm = new LocalLLM(config.build());

        try {
            var result = llm.complete(prompt, defaultOptions());
            assertEquals(Completion.FinishReason.stop, result.get(0).finishReason());
            assertTrue(result.get(0).text().length() > 10);
        } finally {
            llm.deconstruct();
        }
    }

    @Test
    @Disabled
    public void testAsyncGeneration() {
        var sb = new StringBuilder();
        var tokenCount = new AtomicInteger(0);
        var config = new LlmLocalClientConfig.Builder()
                .parallelRequests(1)
                .model(ModelReference.valueOf(model));
        var llm = new LocalLLM(config.build());

        try {
            var future = llm.completeAsync(prompt, defaultOptions(), completion -> {
                sb.append(completion.text());
                tokenCount.incrementAndGet();
            }).exceptionally(exception -> Completion.FinishReason.error);

            assertFalse(future.isDone());
            var reason = future.join();
            assertTrue(future.isDone());
            assertNotEquals(reason, Completion.FinishReason.error);

        } finally {
            llm.deconstruct();
        }
        assertTrue(tokenCount.get() > 0);
        System.out.println(sb);
    }

    @Test
    @Disabled
    public void testParallelGeneration() {
        var prompts = testPrompts();
        var promptsToUse = prompts.size();
        var parallelRequests = 10;

        var futures = new ArrayList<CompletableFuture<Completion.FinishReason>>(Collections.nCopies(promptsToUse, null));
        var completions = new ArrayList<StringBuilder>(Collections.nCopies(promptsToUse, null));
        var tokenCounts = new ArrayList<>(Collections.nCopies(promptsToUse, 0));

        var config = new LlmLocalClientConfig.Builder()
                .parallelRequests(parallelRequests)
                .model(ModelReference.valueOf(model));
        var llm = new LocalLLM(config.build());

        try {
            for (int i = 0; i < promptsToUse; i++) {
                final var seq = i;

                completions.set(seq, new StringBuilder());
                futures.set(seq, llm.completeAsync(StringPrompt.from(prompts.get(seq)), defaultOptions(), completion -> {
                    completions.get(seq).append(completion.text());
                    tokenCounts.set(seq, tokenCounts.get(seq) + 1);
                }).exceptionally(exception -> Completion.FinishReason.error));
            }
            for (int i = 0; i < promptsToUse; i++) {
                var reason = futures.get(i).join();
                assertNotEquals(reason, Completion.FinishReason.error);
            }
        } finally {
            llm.deconstruct();
        }
        for (int i = 0; i < promptsToUse; i++) {
            assertFalse(completions.get(i).isEmpty());
            assertTrue(tokenCounts.get(i) > 0);
        }
    }

    @Test
    @Disabled
    public void testRejection() {
        var prompts = testPrompts();
        var promptsToUse = prompts.size();
        var parallelRequests = 2;
        var additionalQueue = 1;
        // 7 should be rejected

        var futures = new ArrayList<CompletableFuture<Completion.FinishReason>>(Collections.nCopies(promptsToUse, null));
        var completions = new ArrayList<StringBuilder>(Collections.nCopies(promptsToUse, null));

        var config = new LlmLocalClientConfig.Builder()
                .parallelRequests(parallelRequests)
                .maxQueueSize(additionalQueue)
                .model(ModelReference.valueOf(model));
        var llm = new LocalLLM(config.build());

        var rejected = new AtomicInteger(0);
        try {
            for (int i = 0; i < promptsToUse; i++) {
                final var seq = i;

                completions.set(seq, new StringBuilder());
                try {
                    var future = llm.completeAsync(StringPrompt.from(prompts.get(seq)), defaultOptions(), completion -> {
                        completions.get(seq).append(completion.text());
                    }).exceptionally(exception -> Completion.FinishReason.error);
                    futures.set(seq, future);
                } catch (RejectedExecutionException e) {
                    rejected.incrementAndGet();
                }
            }
            for (int i = 0; i < promptsToUse; i++) {
                if (futures.get(i) != null) {
                    assertNotEquals(futures.get(i).join(), Completion.FinishReason.error);
                }
            }
        } finally {
            llm.deconstruct();
        }
        assertEquals(7, rejected.get());
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

}
