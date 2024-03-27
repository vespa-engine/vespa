// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LlmClientConfig;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class MockLLMClient extends ConfigurableLanguageModel {

    public final static String ACCEPTED_API_KEY = "sesame";

    private final ExecutorService executor;
    private final BiFunction<Prompt, InferenceParameters, String> generator;

    private Prompt lastPrompt;

    public MockLLMClient(LlmClientConfig config,
                         SecretStore secretStore,
                         BiFunction<Prompt, InferenceParameters, String> generator,
                         ExecutorService executor) {
        super(config, secretStore);
        this.generator = generator;
        this.executor = executor;
    }

    private void checkApiKey(InferenceParameters options) {
        var apiKey = getApiKey(options);
        if (apiKey == null || ! apiKey.equals(ACCEPTED_API_KEY)) {
            throw new IllegalArgumentException("Invalid API key");
        }
    }

    private void setPrompt(Prompt prompt) {
        this.lastPrompt = prompt;
    }

    public Prompt getPrompt() {
        return this.lastPrompt;
    }

    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters params) {
        setApiKey(params);
        checkApiKey(params);
        setPrompt(prompt);
        return List.of(Completion.from(this.generator.apply(prompt, params)));
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                                    InferenceParameters params,
                                                                    Consumer<Completion> consumer) {
        setPrompt(prompt);
        var completionFuture = new CompletableFuture<Completion.FinishReason>();
        var completions = this.generator.apply(prompt, params).split(" ");  // Simple tokenization

        long sleep = 1;
        executor.submit(() -> {
            try {
                for (int i=0; i < completions.length; ++i) {
                    String completion = (i > 0 ? " " : "") + completions[i];
                    consumer.accept(Completion.from(completion, Completion.FinishReason.none)); Thread.sleep(sleep);
                }
                completionFuture.complete(Completion.FinishReason.stop);
            } catch (InterruptedException e) {
                // Do nothing
            }
        });

        return completionFuture;
    }

}
