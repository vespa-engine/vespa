
package com.yahoo.search.rendering;

import com.yahoo.search.result.EventStream;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.document.DocumentId;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.text.Utf8;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventRendererTestCase {

    private static ThreadPoolExecutor executor;
    private static EventRenderer blueprint;
    private EventRenderer renderer;

    @BeforeAll
    public static void createExecutorAndBlueprint() {
        ThreadFactory threadFactory = ThreadFactoryFactory.getThreadFactory("test-rendering");
        executor = new ThreadPoolExecutor(4, 4, 1L, TimeUnit.MINUTES, new LinkedBlockingQueue<>(), threadFactory);
        executor.prestartAllCoreThreads();
        blueprint = new EventRenderer(executor);
    }

    @BeforeEach
    public void createClone() {
        // Use the shared renderer as a prototype object, as specified in the API contract
        renderer = (EventRenderer) blueprint.clone();
        renderer.init();
    }

    @AfterEach
    public void deconstructClone() {
        if (renderer != null) {
            renderer.deconstruct();
            renderer = null;
        }
    }

    @AfterAll
    public static void deconstructBlueprintAndExecutor() throws InterruptedException {
        blueprint.deconstruct();
        blueprint = null;
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
            throw new RuntimeException("Failed to shutdown executor");
        }
        executor = null;
    }

    @Test
    @Timeout(5)
    public void testRendering() throws InterruptedException, ExecutionException {
        var expected = """
                event: token
                data: {"token":"Ducks"}
                
                event: token
                data: {"token":" have"}
                
                event: token
                data: {"token":" adorable"}
                
                event: token
                data: {"token":" waddling"}
                
                event: token
                data: {"token":" walks"}
                
                event: end
                """;
        var tokenStream = new EventStream();
        for (String token : splitter("Ducks have adorable waddling walks")) {
            tokenStream.add(token);
        }
        tokenStream.markComplete();
        var result = render(new Result(new Query(), newHitGroup(tokenStream, "token_stream")));
        assertEquals(expected, result);
    }

    @Test
    @Timeout(5)
    public void testAsyncRendering() throws InterruptedException, ExecutionException {
        var expected = """
                event: token
                data: {"token":"Ducks"}
                
                event: token
                data: {"token":" have"}
                
                event: token
                data: {"token":" adorable"}
                
                event: token
                data: {"token":" waddling"}
                
                event: token
                data: {"token":" walks"}
                
                event: end
                """;
        var result = "";
        var executor = Executors.newFixedThreadPool(1);
        try {
            var tokenStream = new EventStream();
            var future = completeAsync("Ducks have adorable waddling walks", executor, token -> {
                tokenStream.add(token);
            }).exceptionally(e -> {
                tokenStream.error("error", new ErrorMessage(400, e.getMessage()));
                tokenStream.markComplete();
                return false;
            }).thenAccept(finishReason -> {
                tokenStream.markComplete();
            });
            assertFalse(future.isDone());
            result = render(new Result(new Query(), newHitGroup(tokenStream, "token_stream")));
            future.join();  // Renderer waits for async completion

        } finally {
            executor.shutdownNow();
        }
        assertEquals(expected, result);
    }

    @Test
    public void testErrorEndsStream() throws ExecutionException, InterruptedException {
        var tokenStream = new EventStream();
        tokenStream.add("token1");
        tokenStream.add("token2");
        tokenStream.error("my_llm", new ErrorMessage(400, "Something went wrong"));
        tokenStream.markComplete();
        var result = render(new Result(new Query(), newHitGroup(tokenStream, "token_stream")));
        var expected = """
                event: token
                data: {"token":"token1"}

                event: token
                data: {"token":"token2"}

                event: error
                data: {"source":"my_llm","error":400,"message":"Something went wrong"}

                event: end
                """;
        assertEquals(expected, result);
    }

    @Test
    public void testPromptRendering() throws ExecutionException, InterruptedException {
        String prompt = "Why are ducks better than cats?\n\nBe concise.\n";

        var tokenStream = new EventStream();
        tokenStream.add(prompt, "prompt");
        tokenStream.add("Just");
        tokenStream.add(" because");
        tokenStream.add(".");
        tokenStream.markComplete();
        var result = render(new Result(new Query(), newHitGroup(tokenStream, "token_stream")));

        var expected = """
                event: prompt
                data: {"prompt":"Why are ducks better than cats?\\n\\nBe concise.\\n"}
                
                event: token
                data: {"token":"Just"}

                event: token
                data: {"token":" because"}

                event: token
                data: {"token":"."}

                event: end
                """;
        assertEquals(expected, result);
    }

    @Test
    @Timeout(5)
    public void testResultRenderingIsSkipped() throws ExecutionException, InterruptedException {
        var tokenStream = new EventStream();
        tokenStream.add("token1");
        tokenStream.add("token2");
        tokenStream.markComplete();

        var resultsHitGroup = new HitGroup("test_results");
        var hit1 = new Hit("result_1");
        var hit2 = new Hit("result_2");
        hit1.setField("documentid", new DocumentId("id:unittest:test::1"));
        hit2.setField("documentid", new DocumentId("id:unittest:test::2"));
        resultsHitGroup.add(hit1);
        resultsHitGroup.add(hit2);

        var combined = new HitGroup("all");
        combined.add(resultsHitGroup);
        combined.add(newHitGroup(tokenStream, "token_stream"));

        var result = render(new Result(new Query(), combined));
        var expected = """
                event: token
                data: {"token":"token1"}

                event: token
                data: {"token":"token2"}

                event: end
                """;
        assertEquals(expected, result);
    }

    static HitGroup newHitGroup(EventStream eventStream, String id) {
        var hitGroup = new HitGroup(id);
        hitGroup.add(eventStream);
        return hitGroup;
    }

    private String render(Result r) throws InterruptedException, ExecutionException {
        var execution = new Execution(Execution.Context.createContextStub());
        return render(execution, r);
    }

    private String render(Execution execution, Result r) throws InterruptedException, ExecutionException {
        if (renderer == null) createClone();
        try {
            ByteArrayOutputStream bs = new ByteArrayOutputStream();  //new DebugOutputStream();
            CompletableFuture<Boolean> f = renderer.renderResponse(bs, r, execution, null);
            assertTrue(f.get());
            return Utf8.toString(bs.toByteArray());
        } finally {
            deconstructClone();
        }
    }

    private static class DebugOutputStream extends ByteArrayOutputStream {
        @Override
        public synchronized void write(byte[] b, int off, int len) {
            super.write(b, off, len);
            System.out.print(new String(b, off, len));
        }
    }

    private static List<String> splitter(String text) {
        var list = new ArrayList<String>();
        for (String token : text.split(" ")) {
            list.add(list.isEmpty() ? token : " " + token);
        }
        return list;
    }

    private static CompletableFuture<Boolean> completeAsync(String text, ExecutorService executor, Consumer<String> consumer) {
        var completionFuture = new CompletableFuture<Boolean>();
        executor.submit(() -> {
            try {
                for (String s : splitter(text)) {
                    consumer.accept(s);
                    Thread.sleep(10);
                }
                completionFuture.complete(true);
            } catch (Exception e) {
                completionFuture.completeExceptionally(e);
            }
        });
        return completionFuture;
    }

}
