// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.client.openai.OpenAiClient;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A configurable OpenAI client.
 *
 * @author lesters
 */
@Beta
public class OpenAI extends ConfigurableLanguageModel {

    private final OpenAiClient client;

    @Inject
    public OpenAI(LlmClientConfig config, SecretStore secretStore) {
        super(config, secretStore);
        client = new OpenAiClient();
    }

    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters parameters) {
        setApiKey(parameters);
        setEndpoint(parameters);
        return client.complete(prompt, parameters);
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                                    InferenceParameters parameters,
                                                                    Consumer<Completion> consumer) {
        setApiKey(parameters);
        setEndpoint(parameters);
        return client.completeAsync(prompt, parameters, consumer);
    }
}

