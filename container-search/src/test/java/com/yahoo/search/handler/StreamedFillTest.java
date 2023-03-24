package com.yahoo.search.handler;

import ai.vespa.cloud.ZoneInfo;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ContainerHttpConfig;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.jdisc.ContentChannelOutputStream;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.searchchain.ExecutionFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Tests filling hits as they are rendered to avoid having to keep all hits in memory.
 *
 * @author bratseth
 */
public class StreamedFillTest {

    @Test
    void testStreamedFill() {
        var handler = new SearchHandler(new MockMetric(),
                                        new SimpleContainerThreadpool(),
                                        new CompiledQueryProfileRegistry(),
                                        new ContainerHttpConfig.Builder().build(),
                                        new ComponentRegistry(),
                                        ExecutionFactory.empty(),
                                        ZoneInfo.defaultInfo());
        var request = HttpRequest.createTestRequest("?query=foo", com.yahoo.jdisc.http.HttpRequest.Method.GET);
        var response = (HttpSearchResponse)handler.handle(request);
        System.out.println(render(response));
    }

    private String render(HttpSearchResponse response) {
        try {
            var out = new ReadableContentChannel();
            response.render(new ContentChannelOutputStream(out), out, null);
            return new String(out.toStream().readAllBytes(), UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class SimpleContainerThreadpool implements ContainerThreadPool {

        private final Executor executor = Executors.newCachedThreadPool();

        @Override public Executor executor() { return executor; }

    }

}
