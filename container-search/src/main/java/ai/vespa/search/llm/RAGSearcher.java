// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.search.llm;

import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import ai.vespa.search.llm.LlmSearcherConfig;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;

import java.util.logging.Logger;

@Beta
public class RAGSearcher extends LLMSearcher {

    private static Logger log = Logger.getLogger(RAGSearcher.class.getName());

    private static final String PROMPT = "prompt";

    @Inject
    public RAGSearcher(LlmSearcherConfig config, ComponentRegistry<LanguageModel> languageModels) {
        super(config, languageModels);
        log.info("Starting " + RAGSearcher.class.getName() + " with language model " + config.providerId());
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);
        execution.fill(result);

        // Todo: Add resulting prompt to the result
        Prompt prompt = buildPrompt(query, result);

        return complete(query, prompt);
    }

    protected Prompt buildPrompt(Query query, Result result) {
        String prompt = getPrompt(query);

        // Replace @query with the actual query
        if (prompt.contains("@query")) {
            prompt = prompt.replace("@query", query.getModel().getQueryString());
        }

        if ( !prompt.contains("{context}")) {
            prompt = "{context}\n" + prompt;
        }
        prompt = prompt.replace("{context}", buildContext(result));
        log.info("Prompt: " + prompt);  // remove
        return StringPrompt.from(prompt);
    }

    private String getPrompt(Query query) {
        // First, check if prompt is set with a prefix
        String propertyWithPrefix = this.getPropertyPrefix() + "." + PROMPT;
        String prompt = query.properties().getString(propertyWithPrefix);
        if (prompt != null)
            return prompt;

        // If not, try without prefix
        prompt = query.properties().getString(PROMPT);
        if (prompt != null)
            return prompt;

        // If not, use query
        prompt = query.getModel().getQueryString();
        if (prompt != null)
            return prompt;

        // If not, throw exception
        throw new IllegalArgumentException("RAG searcher could not find prompt found for query. Tried looking for " +
                "'" + propertyWithPrefix + "." + PROMPT + "', '" + PROMPT + "' or '@query'.");
    }

    private String buildContext(Result result) {
        StringBuilder sb = new StringBuilder();
        var hits = result.hits();
        hits.forEach(hit -> {
            hit.fields().forEach((key, value) -> {
                sb.append(key + ": " + value).append("\n");
            });
            sb.append("\n");
        });
        var context = sb.toString();
        return context;
    }

}
