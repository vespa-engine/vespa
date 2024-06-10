package ai.vespa.search.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.search.Result;
import com.yahoo.search.rendering.EventRenderer;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.text.Utf8;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RAGWithEventRendererTest {

    @Test
    public void testPromptAndHitsAreRendered() throws Exception {
        var params = Map.of(
                "query", "why are ducks better than cats?",
                "llm.stream", "false",
                "llm.includePrompt", "true",
                "llm.includeHits", "true"
        );
        var llm = LLMSearcherTest.createLLMClient();
        var searcher = RAGSearcherTest.createRAGSearcher(Map.of("mock", llm));
        var results = RAGSearcherTest.runMockSearch(searcher, params);

        var result = render(results);

        var promptEvent = extractEvent(result, "prompt");
        assertNotNull(promptEvent);
        assertTrue(promptEvent.has("prompt"));

        var resultsEvent = extractEvent(result, "hits");
        assertNotNull(resultsEvent);
        assertTrue(resultsEvent.has("root"));
        assertEquals(2, resultsEvent.get("root").get("children").size());
    }

    private JsonNode extractEvent(String result, String eventName) throws JsonProcessingException {
        var lines = result.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("event: " + eventName)) {
                var data = lines[i + 1].substring("data: ".length()).trim();
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readTree(data);
            }
        }
        return null;
    }

    private String render(Result r) throws InterruptedException, ExecutionException {
        var execution = new Execution(Execution.Context.createContextStub());
        return render(execution, r);
    }

    private String render(Execution execution, Result r) throws ExecutionException, InterruptedException {
        var renderer = new EventRenderer();
        try {
            renderer.init();
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            CompletableFuture<Boolean> f = renderer.renderResponse(bs, r, execution, null);
            assertTrue(f.get());
            return Utf8.toString(bs.toByteArray());
        } finally {
            renderer.deconstruct();
        }
    }

}
