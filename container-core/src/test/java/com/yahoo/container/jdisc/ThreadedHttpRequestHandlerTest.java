// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.jdisc.Metric;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author bjorncs
 */
public class ThreadedHttpRequestHandlerTest {

    @Test
    public void unhandled_exception_metric_is_incremented_if_subclassed_handler_throws_exception() {
        MetricMock metricMock = new MetricMock();
        ThreadedHttpRequestHandlerThrowingException handler = new ThreadedHttpRequestHandlerThrowingException(metricMock);
        RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler);

        driver.sendRequest("http://localhost/myhandler");
        String expectedMetricName = "jdisc.http.handler.unhandled_exceptions";
        assertThat(metricMock.addInvocations)
                .containsKey(expectedMetricName);
        assertThat(metricMock.addInvocations.get(expectedMetricName).dimensions)
                .containsEntry("exception", "DummyException");
    }

    private static class MetricMock implements Metric {
        final ConcurrentHashMap<String, SimpleMetricContext> addInvocations = new ConcurrentHashMap<>();

        @Override public void add(String key, Number val, Context ctx) {
            addInvocations.put(key, (SimpleMetricContext)ctx);
        }
        @Override public void set(String key, Number val, Context ctx) {}
        @Override public Context createContext(Map<String, ?> properties) { return new SimpleMetricContext(properties); }
    }

    private static class SimpleMetricContext implements Metric.Context {
        final Map<String, String> dimensions;

        @SuppressWarnings("unchecked")
        SimpleMetricContext(Map<String, ?> dimensions) { this.dimensions = (Map<String, String>)dimensions; }
    }

    private static class ThreadedHttpRequestHandlerThrowingException extends ThreadedHttpRequestHandler {
        ThreadedHttpRequestHandlerThrowingException(Metric metric) {
            super(Executors.newSingleThreadExecutor(), metric);
        }
        @Override public HttpResponse handle(HttpRequest request) { throw new DummyException(); }
    }

    private static class DummyException extends RuntimeException {}
}