// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.llm;

import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.clients.LlmClientConfig;
import ai.vespa.llm.clients.OpenAI;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.EventStream;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;


import java.util.Map;

@Disabled("Disabled until we have a proper mock or surrogate server to run in unit tests")
public class RAGSearcherWithOpenAITest {
    private static final String API_KEY = "<YOUR_API_KEY>";

    private static final String DOC1_TITLE = "Exploring the Delightful Qualities of Ducks";
    private static final String DOC1_CONTENT = "Ducks, with their gentle quacks and adorable waddling walks, possess a unique " +
            "charm that sets them apart as extraordinary pets.";
    private static final String DOC2_TITLE = "Why Cats Reign Supreme";
    private static final String DOC2_CONTENT = "Cats bring an enchanting allure to households with their independent " +
            "companionship, playful nature, natural hunting abilities, low-maintenance grooming, and the " +
            "emotional support they offer.";


    public static class MockSearchResults extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Hit hit1 = new Hit("1");
            hit1.setField("title", DOC1_TITLE);
            hit1.setField("content", DOC1_CONTENT);
            hit1.setField("matchfeatures", "...");

            Hit hit2 = new Hit("2");
            hit2.setField("title", DOC2_TITLE);
            hit2.setField("content", DOC2_CONTENT);
            hit2.setField("summaryfeatures", "...");
            hit2.setField("rankfeatures", "...");

            Result r = new Result(query);
            r.hits().add(hit1);
            r.hits().add(hit2);
            return r;
        }
    }
    
    @Test
    public void testStreaming() {
        var llmConfig = new LlmClientConfig.Builder()
                .apiKeySecretName("openai")
                .maxTokens(100)
                .build();
        var openai = new OpenAI(llmConfig, new MockSecrets());

        var searcherConfig = new LlmSearcherConfig.Builder().stream(true).build();
        ComponentRegistry<LanguageModel> models = new ComponentRegistry<>();
        models.register(ComponentId.fromString("openai"), openai);
        models.freeze();
        var searcher = new RAGSearcher(searcherConfig, models);

        var chain = new Chain<>(searcher, new MockSearchResults());
        var execution = new Execution(chain, Execution.Context.createContextStub());
        var queryParams = Map.of(
                "query", "why are ducks better than cats?",
                "llm.fields", "title,content",
                "llm.prompt", "{context}\nGiven these documents, answer this query as concisely as possible: @query"
        );
        var query = new Query("?" + LLMSearcherTest.toUrlParams(queryParams));

        var result = execution.search(query);
        var stream = (EventStream) result.hits().get(0);
        // assert that stream is not complete - (if completeAsync had been blocking, it would be complete)
        // For this assertion to fail, OpenAI-completion must have been completed in less time than the execution time
        // of returning the search. Consider this more robust than asserting on timings.
        assertFalse(stream.incoming().isComplete());
    }

    static class MockSecrets implements Secrets {
        private final String apiKeyValue;

        // Default constructor uses the constant API_KEY
        MockSecrets() {
            this(API_KEY);
        }

        // Constructor that allows specifying a custom API key
        MockSecrets(String apiKeyValue) {
            this.apiKeyValue = apiKeyValue;
        }

        @Override
        public Secret get(String key) {
            if (key.equals("openai")) {
                return new Secret() {
                    @Override
                    public String current() {
                        return apiKeyValue;
                    }
                };
            }
            return null;
        }
    }
}
