// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.test.MetricMock;
import com.yahoo.jdisc.Metric;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
public class ThreadedHttpRequestHandlerTest {

    @Test
    void unhandled_exceptions_metric_is_incremented_if_subclassed_handler_throws_exception() {
        MetricMock metricMock = new MetricMock();
        ThreadedHttpRequestHandlerThrowingException handler = new ThreadedHttpRequestHandlerThrowingException(metricMock);
        RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler);

        driver.sendRequest("http://localhost/myhandler").readAll();
        String expectedMetricName = "jdisc.http.handler.unhandled_exceptions";
        assertTrue(metricMock.innvocations()
                .containsKey(expectedMetricName));
        assertEquals("DummyException",
                ((MetricMock.SimpleMetricContext) metricMock.innvocations().get(expectedMetricName).ctx).dimensions.get("exception"));
    }


    private static class ThreadedHttpRequestHandlerThrowingException extends ThreadedHttpRequestHandler {
        ThreadedHttpRequestHandlerThrowingException(Metric metric) {
            super(Executors.newSingleThreadExecutor(), metric);
        }
        @Override public HttpResponse handle(HttpRequest request) { throw new DummyException(); }
    }

    private static class DummyException extends RuntimeException {}
}
