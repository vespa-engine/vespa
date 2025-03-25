// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.llm;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.clients.LlmClientConfig;
import ai.vespa.llm.clients.OpenAI;
import ai.vespa.llm.completion.StringPrompt;
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

import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class RAGSearcherWithOpenAITest {
    private static final String API_KEY = "";

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
    @Disabled
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


        var startTime = System.currentTimeMillis();
        var result = execution.search(query);
        var endTime = System.currentTimeMillis();
        assert (endTime - startTime < 1000);
//        
//        
//        var stream = (EventStream) result.hits().get(0);
//        //stream.completeFuture().join();
//        try {
//            Thread.sleep(1000);
//            var data = stream.incoming().drain();
//            assert(data.size() > 1);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
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
