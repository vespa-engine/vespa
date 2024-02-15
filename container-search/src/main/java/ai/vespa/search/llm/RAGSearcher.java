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

    @Inject
    public RAGSearcher(LlmSearcherConfig config, ComponentRegistry<LanguageModel> languageModels) {
        super(config, languageModels);
        log.info("Starting " + RAGSearcher.class.getName() + " with language model " + config.providerId());
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);
        execution.fill(result);
        return complete(query, buildPrompt(query, result));
    }

    protected Prompt buildPrompt(Query query, Result result) {
        String propertyWithPrefix = this.getPropertyPrefix() + ".prompt";
        String prompt = query.properties().getString(propertyWithPrefix);
        if (prompt == null) {
            prompt = "Please provide a summary of the above";
        }
        if (!prompt.contains("{context}")) {
            prompt = "{context}\n" + prompt;
        }
        // Todo: support system and user prompt
        prompt = prompt.replace("{context}", buildContext(result));
        log.info("Prompt: " + prompt);  // remove
        return StringPrompt.from(prompt);
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
