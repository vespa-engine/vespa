// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static com.yahoo.jdisc.Response.Status.OK;
import static org.cthul.matchers.CthulMatchers.isA;
import static org.cthul.matchers.CthulMatchers.matchesPattern;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class HttpServerMetricTest {

    @Test(enabled = false)
    public void requireThatNumActiveRequestsIsTracked() throws Exception {
        final MetricConsumer metricConsumer = mock(MetricConsumer.class);
        final TestDriver driver = TestDrivers.newInstance(
                new EchoRequestHandler(),
                newMetricModule(metricConsumer));
        driver.client().get("/status.html")
              .expectStatusCode(is(OK));
        final InOrder order = inOrder(metricConsumer);
        order.verify(metricConsumer).set(eq("serverNumActiveRequests"), eq(1), any(Metric.Context.class));
        order.verify(metricConsumer).set(eq("serverNumActiveRequests"), eq(0), any(Metric.Context.class));
        assertThat(driver.close(), is(true));
    }

    @SuppressWarnings("deprecation")
    @Test(enabled = false)
    public void requireThatCustomMetricDimensionsAreSupported() throws Exception {
        final MetricConsumer metricConsumer = mock(MetricConsumer.class);
        // TODO: enable metrics
        final ConnectorConfig.Builder connectorConfig = new ConnectorConfig.Builder();

        final Map<String, String> commonDimensions = new HashMap<>();
        commonDimensions.put("key1", "value1");
        commonDimensions.put("key2", "value2");
        // TODO: serverConfig.commonMetricDimensions().add(...);

        final TestDriver driver = TestDrivers.newConfiguredInstance(
                new EchoRequestHandler(),
                new ServerConfig.Builder(),
                connectorConfig,
                newMetricModule(metricConsumer));
        driver.client().get("/status.html")
              .expectStatusCode(is(OK));

        final ArgumentCaptor<Map<String, ?>> contextCaptor = new ArgumentCaptor<>();
        verify(metricConsumer).createContext(contextCaptor.capture());
        final Map<String, ?> actualContext = contextCaptor.getValue();
        for (final Map.Entry<String, String> entry : commonDimensions.entrySet()) {
            assertThat(actualContext.get(entry.getKey()), isA(String.class).that(is(entry.getValue())));
        }
        assertThat(actualContext.get("serverName"), isA(String.class).that(matchesPattern("\\S+")));
        assertThat(actualContext.get("serverPort"), isA(String.class).that(matchesPattern("\\d+")));
        assertThat(driver.close(), is(true));
    }

    private static Module newMetricModule(final MetricConsumer metricConsumer) {
        return new AbstractModule() {

            @Override
            protected void configure() {
                bind(MetricConsumer.class).toInstance(metricConsumer);
            }
        };
    }

    private static class EchoRequestHandler extends AbstractRequestHandler {

        @Override
        public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
            return handler.handleResponse(new Response(OK));
        }
    }
}
