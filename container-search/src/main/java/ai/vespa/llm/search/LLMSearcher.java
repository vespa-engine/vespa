// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.search;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.LanguageModelException;
import ai.vespa.llm.LlmSearcherConfig;
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
import com.yahoo.search.result.HitGroup;

import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Base class for LLM searchers. Provides utilities for calling LLMs and handling properties.
 *
 * @author lesters
 */
@Beta
public abstract class LLMSearcher extends Searcher {

    private static Logger log = Logger.getLogger(LLMSearcher.class.getName());

    private static final String API_KEY_HEADER = "X-LLM-API-KEY";
    private static final String STREAM_PROPERTY = "stream";
    private static final String PROMPT_PROPERTY = "prompt";

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
            var entry = languageModels.allComponentsById().entrySet().stream().findFirst();
            if (entry.isEmpty()) {
                throw new IllegalArgumentException("No language models were found");  // shouldn't happen given check above
            }
            log.info("Language model provider was not found in config. " +
                     "Fallback to using first available language model: " + entry.get().getKey());
            return entry.get().getValue();
        }
        final LanguageModel languageModel = languageModels.getComponent(providerId);
        if (languageModel == null) {
            throw new IllegalArgumentException("No component with id '" + providerId + "' was found. " +
                    "Available LLM components are: " + languageModels.allComponentsById().keySet().stream()
                                                       .map(ComponentId::toString).collect(Collectors.joining(",")));
        }
        return languageModel;
    }

    Result complete(Query query, Prompt prompt) {
        var options = new InferenceParameters(getApiKeyHeader(query), s -> lookupProperty(s, query));
        var stream = lookupPropertyBool(STREAM_PROPERTY, query, this.stream);  // query value overwrites config
        return stream ? completeAsync(query, prompt, options) : completeSync(query, prompt, options);
    }

    private Result completeAsync(Query query, Prompt prompt, InferenceParameters options) {
        EventStream eventStream = new EventStream();

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

        HitGroup hitGroup = new HitGroup("token_stream");
        hitGroup.add(eventStream);
        return new Result(query, hitGroup);
    }

    private Result completeSync(Query query, Prompt prompt, InferenceParameters options) {
        EventStream eventStream = new EventStream();

        if (query.getTrace().getLevel() >= 1) {
            eventStream.add(prompt.asString(), "prompt");
        }

        List<Completion> completions = languageModel.complete(prompt, options);
        eventStream.add(completions.get(0).text(), "completion");
        eventStream.markComplete();

        HitGroup hitGroup = new HitGroup("completion");
        hitGroup.add(eventStream);
        return new Result(query, hitGroup);
    }

    String getPrompt(Query query) {
        // Look for prompt with or without prefix
        String prompt = lookupPropertyWithOrWithoutPrefix(PROMPT_PROPERTY, p -> query.properties().getString(p));
        if (prompt != null)
            return prompt;

        // If not found, use query directly
        prompt = query.getModel().getQueryString();
        if (prompt != null)
            return prompt;

        // If not, throw exception
        throw new IllegalArgumentException("Could not find prompt found for query. Tried looking for " +
                "'" + propertyPrefix + "." + PROMPT_PROPERTY + "', '" + PROMPT_PROPERTY + "' or '@query'.");
    }

    String getPropertyPrefix() {
        return this.propertyPrefix;
    }

    String lookupProperty(String property, Query query) {
        String propertyWithPrefix = this.propertyPrefix + "." + property;
        return query.properties().getString(propertyWithPrefix, null);
    }

    Boolean lookupPropertyBool(String property, Query query, boolean defaultValue) {
        String propertyWithPrefix = this.propertyPrefix + "." + property;
        return query.properties().getBoolean(propertyWithPrefix, defaultValue);
    }

    String lookupPropertyWithOrWithoutPrefix(String property, Function<String, String> lookup) {
        String value = lookup.apply(getPropertyPrefix() + "." + property);
        if (value != null)
            return value;
        return lookup.apply(property);
    }

    String getApiKeyHeader(Query query) {
        return lookupPropertyWithOrWithoutPrefix(API_KEY_HEADER, p -> query.getHttpRequest().getHeader(p));
    }

}
