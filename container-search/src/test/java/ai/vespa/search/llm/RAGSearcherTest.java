// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.search.llm;

import ai.vespa.llm.LanguageModel;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;



public class RAGSearcherTest {

    @Test
    public void testRAGGeneration() {
        var llm = LLMSearcherTest.createLLMInterface();
        var searcher = createRAGSearcher(Map.of("mock", llm));
        var result = runMockSearch(searcher, Map.of("llm.prompt", "why are ducks better than cats?"));

        // Prompt generation
        assertTrue(llm.getPrompt().asString().startsWith("title: Exploring the Delightful Qualities of Ducks"));
        assertTrue(llm.getPrompt().asString().endsWith("why are ducks better than cats?"));

        // Result
        assertEquals(1, result.getHitCount());
        assertEquals("Ducks have adorable waddling walks.", result.hits().get(0).getField("completion").toString());
    }

    public static class MockSearchResults extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Hit hit1 = new Hit("1");
            hit1.setField("title", "Exploring the Delightful Qualities of Ducks");
            hit1.setField("content", "Ducks, with their gentle quacks and adorable waddling walks, possess a unique " +
                    "charm that sets them apart as extraordinary pets.");

            Hit hit2 = new Hit("2");
            hit2.setField("title", "Why Cats Reign Supreme");
            hit2.setField("content", "Cats bring an enchanting allure to households with their independent " +
                    "companionship, playful nature, natural hunting abilities, low-maintenance grooming, and the " +
                    "emotional support they offer.");

            Result r = new Result(query);
            r.hits().add(hit1);
            r.hits().add(hit2);
            return r;
        }
    }

    static Result runMockSearch(Searcher searcher, Map<String, String> parameters) {
        Chain<Searcher> chain = new Chain<>(searcher, new MockSearchResults());
        Execution execution = new Execution(chain, Execution.Context.createContextStub());
        Query query = new Query("?" + LLMSearcherTest.toUrlParams(parameters));
        return execution.search(query);
    }

    private static Searcher createRAGSearcher(Map<String, LanguageModel> llms) {
        var config = new LlmSearcherConfig.Builder().stream(false).build();
        ComponentRegistry<LanguageModel> models = new ComponentRegistry<>();
        llms.forEach((key, value) -> models.register(ComponentId.fromString(key), value));
        models.freeze();
        return new RAGSearcher(config, models);
    }

}
