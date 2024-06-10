// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.llm;

import ai.vespa.llm.LanguageModel;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.EventStream;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class RAGSearcherTest {

    private static final String DOC1_TITLE = "Exploring the Delightful Qualities of Ducks";
    private static final String DOC1_CONTENT = "Ducks, with their gentle quacks and adorable waddling walks, possess a unique " +
            "charm that sets them apart as extraordinary pets.";
    private static final String DOC2_TITLE = "Why Cats Reign Supreme";
    private static final String DOC2_CONTENT = "Cats bring an enchanting allure to households with their independent " +
            "companionship, playful nature, natural hunting abilities, low-maintenance grooming, and the " +
            "emotional support they offer.";

    @Test
    public void testRAGGeneration() {
        var eventStream = runRAGQuery(Map.of(
                "prompt", "why are ducks better than cats?",
                "traceLevel", "1"));
        var events = eventStream.incoming().drain();
        assertEquals(2, events.size());

        // Generated prompt
        var promptEvent = (EventStream.Event) events.get(0);
        assertEquals("prompt", promptEvent.type());
        assertEquals("title: " + DOC1_TITLE + "\n" +
                     "content: " + DOC1_CONTENT + "\n\n" +
                     "title: " + DOC2_TITLE + "\n" +
                     "content: " + DOC2_CONTENT + "\n\n\n" +
                     "why are ducks better than cats?", promptEvent.toString());

        // Generated completion
        var completionEvent = (EventStream.Event) events.get(1);
        assertEquals("completion", completionEvent.type());
        assertEquals("Ducks have adorable waddling walks.", completionEvent.toString());
    }

    @Test
    public void testPromptGeneration() {
        var eventStream = runRAGQuery(Map.of(
                "query", "why are ducks better than cats?",
                "prompt", "{context}\nGiven these documents, answer this query as concisely as possible: @query",
                "traceLevel", "1"));
        var events = eventStream.incoming().drain();

        var promptEvent = (EventStream.Event) events.get(0);
        assertEquals("prompt", promptEvent.type());
        assertEquals("title: " + DOC1_TITLE + "\n" +
                "content: " + DOC1_CONTENT + "\n\n" +
                "title: " + DOC2_TITLE + "\n" +
                "content: " + DOC2_CONTENT + "\n\n\n" +
                "Given these documents, answer this query as concisely as possible: " +
                "why are ducks better than cats?", promptEvent.toString());
    }

    @Test
    public void testSkipContextInPrompt() {
        var eventStream = runRAGQuery(Map.of(
                "query", "why are ducks better than cats?",
                "llm.context", "skip",
                "traceLevel", "1"));
        var events = eventStream.incoming().drain();

        var promptEvent = (EventStream.Event) events.get(0);
        assertEquals("prompt", promptEvent.type());
        assertEquals("why are ducks better than cats?", promptEvent.toString());
    }

    public static class MockSearchResults extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Hit hit1 = new Hit("1");
            hit1.setField("title", DOC1_TITLE);
            hit1.setField("content", DOC1_CONTENT);

            Hit hit2 = new Hit("2");
            hit2.setField("title", DOC2_TITLE);
            hit2.setField("content", DOC2_CONTENT);

            Result r = new Result(query);
            r.hits().add(hit1);
            r.hits().add(hit2);
            return r;
        }
    }

    private EventStream runRAGQuery(Map<String, String> params) {
        var llm = LLMSearcherTest.createLLMClient();
        var searcher = createRAGSearcher(Map.of("mock", llm));
        var result = runMockSearch(searcher, params);
        return (EventStream) result.hits().get(0);
    }

    static Result runMockSearch(Searcher searcher, Map<String, String> parameters) {
        Chain<Searcher> chain = new Chain<>(searcher, new MockSearchResults());
        Execution execution = new Execution(chain, Execution.Context.createContextStub());
        Query query = new Query("?" + LLMSearcherTest.toUrlParams(parameters));
        return execution.search(query);
    }

    static Searcher createRAGSearcher(Map<String, LanguageModel> llms) {
        var config = new LlmSearcherConfig.Builder().stream(false).build();
        ComponentRegistry<LanguageModel> models = new ComponentRegistry<>();
        llms.forEach((key, value) -> models.register(ComponentId.fromString(key), value));
        models.freeze();
        return new RAGSearcher(config, models);
    }

}
