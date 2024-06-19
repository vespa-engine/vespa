// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.llm;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.LanguageModelException;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.ComponentId;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.JsonRenderer;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.EventStream;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.text.Utf8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Base class for LLM searchers. Provides utilities for calling LLMs and handling properties.
 *
 * @author lesters
 */
@Beta
public class LLMSearcher extends Searcher {

    private static final Logger log = Logger.getLogger(LLMSearcher.class.getName());

    private static final String API_KEY_HEADER = "X-LLM-API-KEY";
    private static final String STREAM_PROPERTY = "stream";
    private static final String PROMPT_PROPERTY = "prompt";
    private static final String INCLUDE_PROMPT_IN_RESULT = "includePrompt";
    private static final String INCLUDE_HITS_IN_RESULT = "includeHits";

    private final JsonRenderer jsonRenderer;

    private final String propertyPrefix;
    private final String prompt;
    private final boolean stream;
    private final LanguageModel languageModel;
    private final String languageModelId;

    @Inject
    public LLMSearcher(LlmSearcherConfig config, ComponentRegistry<LanguageModel> languageModels) {
        this.stream = config.stream();
        this.languageModelId = config.providerId();
        this.languageModel = findLanguageModel(languageModelId, languageModels);
        this.propertyPrefix = config.propertyPrefix();
        this.prompt = loadDefaultPrompt(config);

        this.jsonRenderer = new JsonRenderer();
    }

    @Override
    public Result search(Query query, Execution execution) {
        return complete(query, StringPrompt.from(getPrompt(query)), null, execution);
    }

    private String loadDefaultPrompt(LlmSearcherConfig config) {
        if (config.prompt() != null && ! config.prompt().isEmpty()) {
            return config.prompt();
        } else if (config.promptTemplate().isPresent()) {
            Path path = config.promptTemplate().get();
            try {
                return new String(Files.readAllBytes(path));
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read prompt template file: " + path, e);
            }
        }
        return null;
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

    protected Result complete(Query query, Prompt prompt, Result result, Execution execution) {
        var options = new InferenceParameters(getApiKeyHeader(query), s -> lookupProperty(s, query));
        var stream = lookupPropertyBool(STREAM_PROPERTY, query, this.stream);  // query value overwrites config
        try {
            if (stream) {
                return completeAsync(query, prompt, options, result, execution);
            }
            return completeSync(query, prompt, options, result, execution);
        } catch (RejectedExecutionException e) {
            return new Result(query, new ErrorMessage(429, e.getMessage()));
        }
    }

    private boolean shouldAddPrompt(Query query) {
        var includePrompt = lookupPropertyBool(INCLUDE_PROMPT_IN_RESULT, query, false);
        return query.getTrace().getLevel() >= 1 || includePrompt;
    }

    private boolean shouldAddTokenStats(Query query) {
        return query.getTrace().getLevel() >= 1;
    }

    private Result completeAsync(Query query, Prompt prompt, InferenceParameters options, Result result, Execution execution) {
        final EventStream eventStream = new EventStream();

        if (shouldAddPrompt(query)) {
            eventStream.add(prompt.asString(), "prompt");
        }
        if (shouldAddHits(query) && result != null) {
            eventStream.add(renderHits(result, execution), "hits");
        }

        final TokenStats tokenStats = new TokenStats();
        languageModel.completeAsync(prompt, options, completion -> {
            tokenStats.onToken();
            handleCompletion(eventStream, completion);
        }).exceptionally(exception -> {
            handleException(eventStream, exception);
            eventStream.markComplete();
            return Completion.FinishReason.error;
        }).thenAccept(finishReason -> {
            tokenStats.onCompletion();
            if (shouldAddTokenStats(query)) {
                eventStream.add(tokenStats.report(), "stats");
            }
            eventStream.markComplete();
        });

        HitGroup hitGroup = new HitGroup("token_stream");
        hitGroup.add(eventStream);
        return new Result(query, hitGroup);
    }

    private void handleCompletion(EventStream eventStream, Completion completion) {
        if (completion.finishReason() == Completion.FinishReason.error) {
            eventStream.add(completion.text(), "error");
        } else {
            eventStream.add(completion.text());
        }
    }

    private void handleException(EventStream eventStream, Throwable exception) {
        int errorCode = 400;
        if (exception instanceof LanguageModelException languageModelException) {
            errorCode = languageModelException.code();
        }
        eventStream.error(languageModelId, new ErrorMessage(errorCode, exception.getMessage()));
    }

    private Result completeSync(Query query, Prompt prompt, InferenceParameters options, Result result, Execution execution) {
        EventStream eventStream = new EventStream();

        if (shouldAddPrompt(query)) {
            eventStream.add(prompt.asString(), "prompt");
        }
        if (shouldAddHits(query) && result != null) {
            eventStream.add(renderHits(result, execution), "hits");
        }

        List<Completion> completions = languageModel.complete(prompt, options);
        eventStream.add(completions.get(0).text(), "completion");
        eventStream.markComplete();

        HitGroup hitGroup = new HitGroup("completion");
        hitGroup.add(eventStream);
        return new Result(query, hitGroup);
    }

    public String getPrompt(Query query) {
        // Look for prompt with or without prefix given in query
        String prompt = lookupPropertyWithOrWithoutPrefix(PROMPT_PROPERTY, p -> query.properties().getString(p));
        if (prompt != null)
            return prompt;

        // Look for prompt defined in config
        if (this.prompt != null && ! this.prompt.isEmpty())
            return this.prompt;

        // If not found, use query directly
        prompt = query.getModel().getQueryString();
        if (prompt != null)
            return prompt;

        // If not, throw exception
        throw new IllegalArgumentException("Could not find prompt found for query. Tried looking for " +
                "'" + propertyPrefix + "." + PROMPT_PROPERTY + "', '" + PROMPT_PROPERTY + "' or '@query'.");
    }

    public String getPropertyPrefix() {
        return this.propertyPrefix;
    }

    public String lookupProperty(String property, Query query) {
        String propertyWithPrefix = this.propertyPrefix + "." + property;
        return query.properties().getString(propertyWithPrefix, null);
    }

    public Boolean lookupPropertyBool(String property, Query query, boolean defaultValue) {
        String propertyWithPrefix = this.propertyPrefix + "." + property;
        return query.properties().getBoolean(propertyWithPrefix, defaultValue);
    }

    public String lookupPropertyWithOrWithoutPrefix(String property, Function<String, String> lookup) {
        String value = lookup.apply(getPropertyPrefix() + "." + property);
        if (value != null)
            return value;
        return lookup.apply(property);
    }

    public String getApiKeyHeader(Query query) {
        return lookupPropertyWithOrWithoutPrefix(API_KEY_HEADER, p -> query.getHttpRequest().getHeader(p));
    }

    private boolean shouldAddHits(Query query) {
        return lookupPropertyBool(INCLUDE_HITS_IN_RESULT, query, false);
    }

    private String renderHits(Result results, Execution execution) {
        var bs = new ByteArrayOutputStream();
        var renderer = jsonRenderer.clone();
        renderer.init();
        renderer.renderResponse(bs, results, execution, null).join();  // wait for renderer to complete
        return Utf8.toString(bs.toByteArray());
    }

    private static class TokenStats {

        private final long start;
        private long timeToFirstToken;
        private long timeToLastToken;
        private long tokens = 0;

        TokenStats() {
            start = System.currentTimeMillis();
        }

        void onToken() {
            if (tokens == 0) {
                timeToFirstToken = System.currentTimeMillis() - start;
            }
            tokens++;
        }

        void onCompletion() {
            timeToLastToken = System.currentTimeMillis() - start;
        }

        String report() {
            return "Time to first token: " + timeToFirstToken + " ms, " +
                   "Generation time: " + timeToLastToken + " ms, " +
                   "Generated tokens: " + tokens + " " +
                   String.format("(%.2f tokens/sec)", tokens / (timeToLastToken / 1000.0));
        }

    }

}
