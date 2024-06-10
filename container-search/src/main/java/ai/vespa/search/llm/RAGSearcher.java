// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.llm;

import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;

import java.util.logging.Logger;

/**
 * An LLM searcher that uses the RAG (Retrieval-Augmented Generation) model to generate completions.
 * Prompts are generated based on the search result context.
 * By default, the context is a concatenation of the fields of the search result hits.
 *
 * @author lesters
 */
@Beta
public class RAGSearcher extends LLMSearcher {

    private static Logger log = Logger.getLogger(RAGSearcher.class.getName());

    private static final String CONTEXT_PROPERTY = "context";

    @Inject
    public RAGSearcher(LlmSearcherConfig config, ComponentRegistry<LanguageModel> languageModels) {
        super(config, languageModels);
        log.info("Starting " + RAGSearcher.class.getName() + " with language model " + config.providerId());
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);
        execution.fill(result);
        return complete(query, buildPrompt(query, result), result, execution);
    }

    protected Prompt buildPrompt(Query query, Result result) {
        String prompt = getPrompt(query);

        // Replace @query with the actual query
        if (prompt.contains("@query")) {
            prompt = prompt.replace("@query", query.getModel().getQueryString());
        }

        String context = lookupProperty(CONTEXT_PROPERTY, query);
        if (context == null || !context.equals("skip")) {
            if ( !prompt.contains("{context}")) {
                prompt = "{context}\n" + prompt;
            }
            prompt = prompt.replace("{context}", buildContext(result));
        }
        return StringPrompt.from(prompt);
    }

    private String buildContext(Result result) {
        StringBuilder sb = new StringBuilder();
        var hits = result.hits();
        hits.forEach(hit -> {
            hit.fields().forEach((key, value) -> {
                sb.append(key).append(": ").append(value).append("\n");
            });
            sb.append("\n");
        });
        var context = sb.toString();
        return context;
    }

}
