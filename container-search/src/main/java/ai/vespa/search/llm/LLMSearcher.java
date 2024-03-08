// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.search.llm;

import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.LanguageModelException;
import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.ComponentId;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.EventStream;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Beta
public abstract class LLMSearcher extends Searcher {

    private static Logger log = Logger.getLogger(LLMSearcher.class.getName());

    private static final String API_KEY_HEADER = "X-LLM-API-KEY";
    private static final String STREAM_PROPERTY = "stream";

    private final String propertyPrefix;
    private final boolean stream;
    private final LanguageModel languageModel;
    private final String languageModelId;

    @Inject
    LLMSearcher(LlmSearcherConfig config, ComponentRegistry<LanguageModel> languageModels) {
        this.stream = config.stream();
        this.languageModelId = config.providerId();
        this.languageModel = findLanguageModel(languageModelId, languageModels);
        this.propertyPrefix = config.propertyPrefix();
    }

    private LanguageModel findLanguageModel(String providerId, ComponentRegistry<LanguageModel> languageModels)
            throws IllegalArgumentException
    {
        if (languageModels.allComponents().isEmpty()) {
            throw new IllegalArgumentException("No language models were found");
        }
        if (providerId == null || providerId.isEmpty()) {
            Optional<Map.Entry<ComponentId, LanguageModel>> entry = languageModels.allComponentsById().entrySet().stream().findFirst();
            if (entry.isEmpty()) {
                throw new IllegalArgumentException("No language models were found");
            }
            log.info("LLM provider not found or is empty. Using first available LLM: " + entry.get().getKey());
            return entry.get().getValue();
        }
        final LanguageModel languageModel = languageModels.getComponent(providerId);
        if (languageModel == null) {
            throw new IllegalArgumentException("No component with id '" + providerId + "' was found. " +
                    "Available LLM components are: " +
                    languageModels.allComponentsById().keySet().stream().map(ComponentId::toString).collect(Collectors.joining(",")));
        }
        return languageModel;
    }

    String getPropertyPrefix() {
        return this.propertyPrefix;
    }

    private String lookupProperty(String property, Query query) {
        String propertyWithPrefix = this.propertyPrefix + "." + property;
        return query.properties().getString(propertyWithPrefix, null);
    }

    private String getApiKeyHeader(Query query) {
        var header = this.propertyPrefix + "." + API_KEY_HEADER;
        var apiKey = query.getHttpRequest().getHeader(header);
        if (apiKey != null) {
            return apiKey;
        }
        return query.getHttpRequest().getHeader(API_KEY_HEADER);
    }

    Result complete(Query query, Prompt prompt) {
        // What about if it is in secret store?
        var options = new InferenceParameters(getApiKeyHeader(query), s -> lookupProperty(s, query));

        // "stream" parameter in query overwrites config
        var stream = query.properties().getBoolean(getPropertyPrefix() + "." + STREAM_PROPERTY, this.stream);

        return stream ? completeAsync(query, prompt, options) : completeSync(query, prompt, options);
    }

    private Result completeAsync(Query query, Prompt prompt, InferenceParameters options) {
        EventStream eventStream = EventStream.create("token_stream");

        if (query.getTrace().getLevel() >= 1) {
            eventStream.add(prompt.asString(), "prompt");
        }

        languageModel.completeAsync(prompt, options, token -> {
            eventStream.add(token.text());
        }).exceptionally(exception -> {
            int errorCode = 400;
            if (exception instanceof LanguageModelException languageModelException) {
                errorCode = languageModelException.code();
            }
            eventStream.error(languageModelId, new ErrorMessage(errorCode, exception.getMessage()));
            eventStream.markComplete();
            return Completion.FinishReason.error;
        }).thenAccept(finishReason -> {
            eventStream.markComplete();
        });

        return new Result(query, eventStream);
    }

    private Result completeSync(Query query, Prompt prompt, InferenceParameters options) {
        List<Completion> completions = languageModel.complete(prompt, options);
        String completion = completions.get(0).text();
        Hit hit = new Hit("completion");
        hit.setField("completion", completion);
        HitGroup hits = new HitGroup();
        hits.add(hit);
        return new Result(query, hits);
    }

}
