// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.client.openai.OpenAiClient;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.secret.Secrets;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A configurable OpenAI client.
 *
 * @author lesters
 * @author glebashnik
 */
@Beta
public class OpenAI extends ConfigurableLanguageModel {
    private final OpenAiClient client;
    private final Map<String, String> configOptions;

    @Inject
    public OpenAI(LlmClientConfig config, Secrets secretStore) {
        super(config, secretStore);
        client = new OpenAiClient();

        configOptions = new HashMap<>();

        if (!config.model().isBlank()) {
            configOptions.put(InferenceParameters.OPTION_MODEL, config.model());
        }

        if (config.temperature() >= 0) {
            configOptions.put(InferenceParameters.OPTION_TEMPERATURE, String.valueOf(config.temperature()));
        }

        if (config.maxTokens() >= 0) {
            configOptions.put(InferenceParameters.OPTION_MAX_TOKENS, String.valueOf(config.maxTokens()));
        }

    }
    
    private InferenceParameters prepareParameters(InferenceParameters parameters) {
        setApiKey(parameters);
        setEndpoint(parameters);
        var combinedParameters = parameters.withDefaultOptions(configOptions::get);
        return combinedParameters;
    }

    @Override
    public List<Completion> complete(
            Prompt prompt, InferenceParameters parameters) {
        var preparedParameters = prepareParameters(parameters);
        return client.complete(prompt, preparedParameters);
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(
            Prompt prompt, InferenceParameters parameters, Consumer<Completion> consumer) {
        var preparedParameters = prepareParameters(parameters);
        return client.completeAsync(prompt, preparedParameters, consumer);
    }

}

