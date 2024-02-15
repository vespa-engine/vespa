// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.search.llm;

import ai.vespa.llm.LanguageModel;
import ai.vespa.search.llm.LlmSearcherConfig;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;

import java.util.logging.Logger;

@Beta
public class LLMQuerySearcher extends LLMSearcher {

    private static Logger log = Logger.getLogger(LLMQuerySearcher.class.getName());

    @Inject
    LLMQuerySearcher(LlmSearcherConfig config, ComponentRegistry<LanguageModel> languageModels) {
        super(config, languageModels);
    }

    @Override
    public Result search(Query query, Execution execution) {

        // Build new query based on LLM before searching.

        Result result = execution.search(query);
        execution.fill(result);
        return result;
    }
}
