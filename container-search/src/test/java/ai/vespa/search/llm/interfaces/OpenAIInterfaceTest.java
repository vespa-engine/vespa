// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.search.llm.interfaces;

import ai.vespa.llm.LanguageModel;
import ai.vespa.search.llm.LlmInterfaceConfig;
import ai.vespa.search.llm.LlmSearcherConfig;
import ai.vespa.search.llm.LLMSearcherTest;
import ai.vespa.search.llm.RAGSearcher;
import ai.vespa.search.llm.RAGSearcherTest;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.SecretStoreProvider;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.DefaultErrorHit;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// This should be a test for the language model, not openai searchers
public class OpenAIInterfaceTest {

    private static final String apiKey = "foobar";

    // Change only to interface actually

    @Test
    public void testOpenAIGeneration() {
        var sb = new StringBuilder();
        var executor = Executors.newFixedThreadPool(1);
        try {
            LlmInterfaceConfig config = new LlmInterfaceConfig.Builder().build();
            LLMInterface openai = new OpenAIInterface(config, new SecretStoreProvider().get());

            ComponentRegistry<LanguageModel> models = new ComponentRegistry<>();
            models.register(ComponentId.fromString("openai"), openai);
            models.freeze();

            LlmSearcherConfig c2 = new LlmSearcherConfig.Builder().providerId("openai").build();
            var searcher = new RAGSearcher(c2, models);

            Chain<Searcher> chain = new Chain<>(searcher, new RAGSearcherTest.MockSearchResults());
            Execution execution = new Execution(chain, Execution.Context.createContextStub());
            Map<String, String> params = Map.of(
//                    "llm.prompt", "With one sentence, why are ducks better than cats?"
                    "llm.prompt", "Using the documents above, why are ducks better than cats?"
//                    "llm.temperature", "argh",  // test noe som ikke går til float
//                    "llm.maxTokens", "10"
//                    "llm.model", "gpt-4"
            );
            Query query = new Query("?" + LLMSearcherTest.toUrlParams(params));
            query.getHttpRequest().getJDiscRequest().headers().add("X-LLM-API-KEY", apiKey);
            Result result = execution.search(query);
            assertEquals(0, result.getHitCount());

            var incoming = result.hits().incoming();
            incoming.addNewDataListener(() -> {
                incoming.drain().forEach(hit -> {
                    if (hit instanceof DefaultErrorHit) {
                        throw new RuntimeException("Got an error: " + hit);
                    }
                    sb.append(hit.getField("token"));
                    System.out.println(hit.getField("token"));  // Remove this
                });
            }, executor);

            assertFalse(incoming.isComplete());
            incoming.completedFuture().orTimeout(30, TimeUnit.SECONDS).join();
            assertTrue(incoming.isComplete());

        } finally {
            executor.shutdownNow();  // Timeout?
        }

        System.out.println(sb);
    }

}
