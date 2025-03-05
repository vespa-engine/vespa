// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModelException;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import com.sun.xml.bind.v2.util.EditDistance;
import com.yahoo.config.ModelReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LocalLLM.
 * Tests are disabled to avoid running them automatically as part of the CI/CD.
 * The reason for this is the long time it takes to download (2-5 min) and run LLMs (10-60 seconds).
 * Still, these tests can be triggered manually and are useful for debugging runtime issues.
 * 
 * @author lesters
 * @author glebashnik
 */
@Disabled
public class LocalLLMTest {
    // Tiny LLM tests, which don't verify that the completions make sense.
    private static final String model = "src/test/models/llm/tinyllm.gguf";
    private static final Prompt prompt = StringPrompt.from("A random prompt");

    @Test
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
            assertNotEquals(Completion.FinishReason.error, reason);

        } finally {
            llm.deconstruct();
        }
        assertTrue(tokenCount.get() > 0);
        System.out.println(sb);
    }

    @Test
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
                assertNotEquals(Completion.FinishReason.error, reason);
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
    public void testRejection() {
        var prompts = testPrompts();
        var promptsToUse = prompts.size();
        var parallelRequests = 2;
        var additionalQueue = 100;
        var queueWaitTime = 10;
        // 8 should be rejected due to queue wait time

        var futures = new ArrayList<CompletableFuture<Completion.FinishReason>>(
                Collections.nCopies(promptsToUse, null));
        var completions = new ArrayList<StringBuilder>(Collections.nCopies(promptsToUse, null));

        var config = new LlmLocalClientConfig.Builder()
                .parallelRequests(parallelRequests)
                .maxQueueSize(additionalQueue)
                .maxQueueWait(queueWaitTime)
                .model(ModelReference.valueOf(model));
        var llm = new LocalLLM(config.build());

        var rejected = new AtomicInteger(0);
        var timedOut = new AtomicInteger(0);

        try {
            for (int i = 0; i < promptsToUse; i++) {
                final var seq = i;

                completions.set(seq, new StringBuilder());
                try {
                    var future = llm.completeAsync(StringPrompt.from(prompts.get(seq)), defaultOptions(), completion -> {
                        completions.get(seq).append(completion.text());
                    }).exceptionally(exception -> {
                        if (exception instanceof LanguageModelException lme) {
                            if (lme.code() == 504) {
                                timedOut.incrementAndGet();
                            }
                        }
                        return Completion.FinishReason.error;
                    });
                    futures.set(seq, future);
                } catch (RejectedExecutionException e) {
                    rejected.incrementAndGet();
                }
            }
            for (int i = 0; i < promptsToUse; i++) {
                if (futures.get(i) != null) {
                    futures.get(i).join();
                }
            }
        } finally {
            llm.deconstruct();
        }
        assertEquals(0, rejected.get());
        assertEquals(8, timedOut.get());
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

    // Small LLM tests use a quantized Mistral model ca. 4.3 GB.
    // It produces sensible completions which can be verified as part of the test.
    // Download model from here https://huggingface.co/lmstudio-community/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf
    private static final String SMALL_LLM_PATH = "src/test/models/llm/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf";

    // Using translation task to test that LLM output makes sense with context overflow.
    // If context overflow is not handled correctly, part of the task description will be overwritten and 
    // the output will not be in a target language.
    // This is easier to verify than question-answering tasks.
    private static final String TASK_PROMPT_TEMPLATE = """
            Translate this text to Norwegian, without notes or explanations:
            Text:
            {input}
            Translation:""";

    private record LLMTask(String input, String output) {
    }

    private static final List<LLMTask> TASKS = List.of(
            new LLMTask(
                    "Life is really simple, but we insist on making it complicated.",
                    "Livet er virkelig enkelt, men vi vil gerne gjøre det komplisert."
            ),
            new LLMTask(
                    "It does not matter how slowly you go as long as you do not stop. " +
                            "Persistence and determination are what truly lead to success. " +
                            "Every small step forward builds the foundation for achieving greatness, " +
                            "even when the journey feels endless.",
                    "Det er ikke viktig hvor langsomt du går, så lange du ikke stopper. " +
                            "Persistens og bestemthed er det som egentlig fører til succes. " +
                            "Hver liten steg fremover bygger grunnlaget for å nå storhet, " +
                            "selv når reisen føler seg uendelig."
            ),
            new LLMTask(
                    "Our greatest glory is not in never falling, but in rising every time we fall.",
                    "Vores største ære er ikke i aldri å falle, men i å stå opp hver gang vi faller."
            ),
            new LLMTask(
                    "Real knowledge is to know the extent of one’s ignorance.",
                    "Verdenslige kunnskap er å vite grænserne for sin egen uvitenhet."
            ),
            new LLMTask(
                    "When we see men of a contrary character, we should turn inwards and examine ourselves.",
                    "Når vi ser människer med en modsat karakter, bør vi se innad og undersøke oss selv."
            ),
            new LLMTask(
                    "Success depends upon previous preparation, and without such preparation there is sure to be failure.",
                    "Suksess avhenger av forhåndsforberedelse, og uten denne er det sikkerhet for feil."
            ),
            new LLMTask(
                    "The man who moves a mountain begins by carrying away small stones, " +
                            "then moves on to medium-sized rocks, gradually clears larger rocks and boulders, " +
                            "and finally overcomes the most formidable obstacles to achieve his goal, " +
                            "displaying extraordinary perseverance and determination.",
                    "Mannen som flyttet berget starter med å bære borte små sten, " +
                            "derefter flyttet han på med mellemstørre steiner, gradvis fjernet større steiner og stenblokker, " +
                            "og sluttelig overkommet de mest formidable hindrerne for å nå sin mål, " +
                            "med utmærkt tenasje og bestemthet."
            )
    );
    
    private static class CompletionTest {
        private final LocalLLM llm;
        private final String input;
        
        private String expectOutput;
        private String expectNotOutput;
        private Completion.FinishReason expectFinishReason;
        private LanguageModelException expectException;
        
        public CompletionTest(LocalLLM llm, String input) {
            this.llm = llm;
            this.input = input;
        }
        
        public CompletionTest expectOutput(String output) {
            this.expectOutput = output;
            return this;
        }

        public CompletionTest expectNotOutput(String output) {
            this.expectNotOutput = output;
            return this;
        }
        
        public CompletionTest expectFinishReason(Completion.FinishReason finishReason) {
            this.expectFinishReason = finishReason;
            return this;
        }
        
        public CompletionTest expectException(LanguageModelException exception) {
            this.expectException = exception;
            return this;
        }
        
        public void test() {
            var promptStr = TASK_PROMPT_TEMPLATE.replace("{input}", input);
            var inferenceOptions = new InferenceParameters(Map.of("temperature", "0")::get);
            
            if (expectException != null) {
                var exception = assertThrows(LanguageModelException.class, () -> llm.complete(StringPrompt.from(promptStr), inferenceOptions));
                assertEquals(expectException.code(), exception.code());
                assertTrue(exception.getMessage().toLowerCase().contains(expectException.getMessage().toLowerCase()));
                return;
            }

            var completion = llm.complete(StringPrompt.from(promptStr), inferenceOptions);
            var finishReason = completion.get(0).finishReason();
            assertEquals(expectFinishReason, finishReason);
            var completionStr = completion.get(0).text().split("\n")[0].strip();

            if (!completionStr.equals(expectOutput)) {
                System.err.println("Prompt: " + promptStr);
                System.err.println("Expected output: " + expectOutput);
                System.err.println("Actual output: " + completionStr);
            }
            
            // Using edit distance to compare output to account for small variations in LLM output.
            // The threshold is an arbitrary small number.
            // Using an edit distance method from arbitrary library among existing dependencies.
            if (expectOutput != null) {
                var editDistance = EditDistance.editDistance(expectOutput, completionStr);
                var maxEditDistance = expectOutput.length() * 0.05;
                assertTrue(editDistance <= maxEditDistance, 
                        "Expected output edit distance <= " + maxEditDistance + ", got " + editDistance);
            }
            
            if (expectNotOutput != null) {
                var editDistance = EditDistance.editDistance(expectNotOutput, completionStr);
                var maxEditDistance = expectNotOutput.length() * 0.05;
                assertTrue(editDistance >= maxEditDistance,
                        "Expected output edit distance >= " + maxEditDistance + ", got " + editDistance);
            }
        }
    }

    @Test
    public void testMaxPromptTokens() {
        var llmConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(1)
                .contextSize(60)
                .maxPromptTokens(25)
                .seed(42)
                .model(ModelReference.valueOf(SMALL_LLM_PATH));
        var llm = new LocalLLM(llmConfig.build());
        
        var task = TASKS.get(0); 
        
        try {
            new CompletionTest(llm, task.input)
                    .expectOutput("Livet er virkelig enkelt, men vi ønsker alligevel.")
                    .expectFinishReason(Completion.FinishReason.stop)
                    .test();
        } finally {
            llm.deconstruct();
        }
    }
    
    @Test
    public void testMaxEnqueueWait() {
        var llmConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(3)
                .contextSize(2 * 1024)
                .maxQueueSize(3)
                .maxEnqueueWait(100)
                .seed(42)
                .contextOverflowPolicy(LlmLocalClientConfig.ContextOverflowPolicy.NONE)
                .model(ModelReference.valueOf(SMALL_LLM_PATH));

        var llm = new LocalLLM(llmConfig.build());
        var executor = Executors.newFixedThreadPool(7);
        var futures = new ArrayList<CompletableFuture<Void>>();

        try {
            for (var task : TASKS.subList(0, 6)) {
                futures.add(CompletableFuture.runAsync(
                        () ->
                                new CompletionTest(llm, task.input)
                                        .expectOutput(task.output)
                                        .expectFinishReason(Completion.FinishReason.stop)
                                        .test()
                        , executor
                ));
            }

            futures.add(CompletableFuture.runAsync(
                    () ->
                            new CompletionTest(llm, TASKS.get(6).input)
                                    .expectException(new LanguageModelException(504, "Rejected completion due to timeout waiting to add the request to the executor queue"))
                                    .test()
                    , executor
            ));
            
            for (var future : futures) {
                future.join();
            }

            executor.shutdown();
        } finally {
            llm.deconstruct();
        }
    }

    @Test
    public void testContextOverflowPolicyDiscard() {
        var llmConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(1)
                .contextSize(25)
                .seed(42)
                .contextOverflowPolicy(LlmLocalClientConfig.ContextOverflowPolicy.Enum.DISCARD)
                .model(ModelReference.valueOf(SMALL_LLM_PATH));
        
        var llm = new LocalLLM(llmConfig.build());

        var task = TASKS.get(0);
        try {
            new CompletionTest(llm, task.input)
                    .expectOutput("")
                    .expectFinishReason(Completion.FinishReason.discard)
                    .test();
        } finally {
            llm.deconstruct();
        }
    }

    @Test
    public void testContextOverflowPolicyAbort() {
        var llmConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(1)
                .contextSize(25)
                .seed(42)
                .contextOverflowPolicy(LlmLocalClientConfig.ContextOverflowPolicy.Enum.ABORT)
                .model(ModelReference.valueOf(SMALL_LLM_PATH));
        
        var llm = new LocalLLM(llmConfig.build());

        var task = TASKS.get(0);
        try {
            new CompletionTest(llm, task.input)
                    .expectException(new LanguageModelException(413, "context size per request"))
                    .test();
        } finally {
            llm.deconstruct();
        }
    }

    @Test
    public void testParallelGenerationWithLargeContext() {
        var llmConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(3)
                .contextSize(3 * 1024)
                .maxQueueSize(2)
                .seed(42)
                .contextOverflowPolicy(LlmLocalClientConfig.ContextOverflowPolicy.NONE)
                .model(ModelReference.valueOf(SMALL_LLM_PATH));

        var llm = new LocalLLM(llmConfig.build());
        var executor = Executors.newFixedThreadPool(5);
        var futures = new ArrayList<CompletableFuture<Void>>();
        
        try {
            for (var task : TASKS) {
                var future = CompletableFuture.runAsync(() -> 
                        new CompletionTest(llm, task.input)
                                .expectOutput(task.output)
                                .expectFinishReason(Completion.FinishReason.stop)
                                .test()
                        , executor);
                futures.add(future);
            }

            for (var future : futures) {
                future.join();
            }

            executor.shutdown();
        } finally {
             llm.deconstruct();
        }
    }

    @Test
    public void testParallelGenerationWithSmallContextOverflowPolicyNone() {
        var llmConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(5)
                .contextSize( 5 * 100)
                .seed(42)
                .contextOverflowPolicy(LlmLocalClientConfig.ContextOverflowPolicy.NONE)
                .model(ModelReference.valueOf(SMALL_LLM_PATH));

        var llm = new LocalLLM(llmConfig.build());
        var executor = Executors.newFixedThreadPool(5);
        var futures = new ArrayList<CompletableFuture<Void>>();

        try {
            for (var i : List.of(0, 2, 3, 4, 5)) {
                futures.add(CompletableFuture.runAsync(
                        () -> new CompletionTest(llm, TASKS.get(i).input)
                                .expectOutput(TASKS.get(i).output)
                                .expectFinishReason(Completion.FinishReason.stop)
                                .test()
                ));
            }

            for (var i : List.of(1, 6)) {
                futures.add(CompletableFuture.runAsync(
                        () -> new CompletionTest(llm, TASKS.get(i).input)
                                .expectNotOutput(TASKS.get(i).output)
                                .expectFinishReason(Completion.FinishReason.stop)
                                .test()
                ));
            }
            
            for (var future : futures) {
                future.join();
            }

            executor.shutdown();
        } finally {
            llm.deconstruct();
        }
    }

    @Test
    public void testParallelGenerationWithSmallContextOverflowPolicyDiscard() {
        var llmConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(5)
                .contextSize( 5 * 100)
                .maxTokens(50)
                .seed(42)
                .contextOverflowPolicy(LlmLocalClientConfig.ContextOverflowPolicy.DISCARD)
                .model(ModelReference.valueOf(SMALL_LLM_PATH));

        var llm = new LocalLLM(llmConfig.build());
        var executor = Executors.newFixedThreadPool(5);
        var futures = new ArrayList<CompletableFuture<Void>>();

        try {
            for (var i : List.of(0, 2, 3, 4, 5)) {
                futures.add(CompletableFuture.runAsync(
                        () -> new CompletionTest(llm, TASKS.get(i).input)
                                .expectOutput(TASKS.get(i).output)
                                .expectFinishReason(Completion.FinishReason.stop)
                                .test()
                ));
            }

            for (var i : List.of(1, 6)) {
                futures.add(CompletableFuture.runAsync(
                        () -> new CompletionTest(llm, TASKS.get(i).input)
                                .expectOutput("")
                                .expectFinishReason(Completion.FinishReason.discard)
                                .test()
                ));
            }
            
            for (var future : futures) {
                future.join();
            }

            executor.shutdown();
        } finally {
            llm.deconstruct();
        }
    }

    @Test
    public void testParallelGenerationWithSmallContextOverflowPolicyAbort() {
        var llmConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(5)
                .contextSize( 5 * 100)
                .maxTokens(50)
                .seed(42)
                .contextOverflowPolicy(LlmLocalClientConfig.ContextOverflowPolicy.ABORT)
                .model(ModelReference.valueOf(SMALL_LLM_PATH));

        var llm = new LocalLLM(llmConfig.build());
        var executor = Executors.newFixedThreadPool(5);
        var futures = new ArrayList<CompletableFuture<Void>>();

        try {
            for (var i : List.of(0, 2, 3, 4, 5)) {
                futures.add(CompletableFuture.runAsync(
                        () -> new CompletionTest(llm, TASKS.get(i).input)
                                .expectOutput(TASKS.get(i).output)
                                .expectFinishReason(Completion.FinishReason.stop)
                                .test()
                ));
            }

            for (var i : List.of(1, 6)) {
                futures.add(CompletableFuture.runAsync(
                        () -> new CompletionTest(llm, TASKS.get(i).input)
                                .expectException(new LanguageModelException(413, "context size per request"))
                                .test()
                ));
            }

            for (var future : futures) {
                future.join();
            }

            executor.shutdown();
        } finally {
            llm.deconstruct();
        }
    }
    
    @Test
    public void testStructuredOutput() {
        var llmConfig = new LlmLocalClientConfig.Builder()
                .parallelRequests(1)
                .contextSize( 500)
                .maxTokens(500)
                .seed(42)
                .model(ModelReference.valueOf(SMALL_LLM_PATH));

        var llm = new LocalLLM(llmConfig.build());
        var jsonSchema = """
                {
                  "type": "object",
                  "properties": {
                    "article.people": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      }
                    }
                  },
                  "required": [
                    "article.people"
                  ],
                  "additionalProperties": false
                }
                """;

        var inferenceOptions = new InferenceParameters(Map.of(
                InferenceParameters.OPTION_TEMPERATURE, "0", 
                InferenceParameters.OPTION_JSON_SCHEMA, jsonSchema
        )::get);
        
        var promptStr = """
            Extract all names of people from this text:
            Lynda Carter was born on July 24, 1951 in Phoenix, Arizona, USA. She is an actress, known for
            Wonder Woman (1975), The Elder Scrolls IV: Oblivion (2006) and The Dukes of Hazzard (2005).
            She has been married to Robert Altman since January 29, 1984. They have two children.
            The output must strictly adhere to the following JSON format:
            %s
        """.formatted(jsonSchema);
        
        var completions = llm.complete(StringPrompt.from(promptStr), inferenceOptions);
        var completionString = completions.get(0).text().trim();
        
        var expectedCompletionString = """
            {
              "article.people": ["Lynda Carter", "Robert Altman"]
            }
            """.trim();
        
        assertEquals(expectedCompletionString, completionString);
    }
}
