// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.server.jetty.JettyHttpServer.Metrics;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http.MetaData.Response;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpTransport;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.Callback;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author ollivir
 */
public class HttpResponseStatisticsCollectorTest {
    private Connector connector;
    private HttpResponseStatisticsCollector collector = new HttpResponseStatisticsCollector();
    private int httpResponseCode = 500;

    @Test
    public void statistics_are_aggregated_by_category() throws Exception {
        testRequest(300, "GET");
        testRequest(301, "GET");
        testRequest(200, "GET");

        Map<String, Map<String, Long>> stats = collector.takeStatisticsByMethod();
        assertThat(stats.get("GET").get(Metrics.RESPONSES_2XX), equalTo(1L));
        assertThat(stats.get("GET").get(Metrics.RESPONSES_3XX), equalTo(2L));
    }

    @Test
    public void statistics_are_grouped_by_http_method() throws Exception {
        testRequest(200, "GET");
        testRequest(200, "PUT");
        testRequest(200, "POST");
        testRequest(200, "POST");
        testRequest(404, "GET");

        Map<String, Map<String, Long>> stats = collector.takeStatisticsByMethod();
        assertThat(stats.get("GET").get(Metrics.RESPONSES_2XX), equalTo(1L));
        assertThat(stats.get("GET").get(Metrics.RESPONSES_4XX), equalTo(1L));
        assertThat(stats.get("PUT").get(Metrics.RESPONSES_2XX), equalTo(1L));
        assertThat(stats.get("POST").get(Metrics.RESPONSES_2XX), equalTo(2L));
    }

    @Test
    public void statistics_include_grouped_and_single_statuscodes() throws Exception {
        testRequest(401, "GET");
        testRequest(404, "GET");
        testRequest(403, "GET");

        Map<String, Map<String, Long>> stats = collector.takeStatisticsByMethod();
        assertThat(stats.get("GET").get(Metrics.RESPONSES_4XX), equalTo(3L));
        assertThat(stats.get("GET").get(Metrics.RESPONSES_401), equalTo(1L));
        assertThat(stats.get("GET").get(Metrics.RESPONSES_403), equalTo(1L));

    }

    @Test
    public void retrieving_statistics_resets_the_counters() throws Exception {
        testRequest(200, "GET");
        testRequest(200, "GET");

        Map<String, Map<String, Long>> stats = collector.takeStatisticsByMethod();
        assertThat(stats.get("GET").get(Metrics.RESPONSES_2XX), equalTo(2L));

        testRequest(200, "GET");

        stats = collector.takeStatisticsByMethod();
        assertThat(stats.get("GET").get(Metrics.RESPONSES_2XX), equalTo(1L));
    }

    @BeforeTest
    public void initializeCollector() throws Exception {
        Server server = new Server();
        connector = new AbstractConnector(server, null, null, null, 0) {
            @Override
            protected void accept(int acceptorID) throws IOException, InterruptedException {
            }

            @Override
            public Object getTransport() {
                return null;
            }
        };
        collector.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                baseRequest.setHandled(true);
                baseRequest.getResponse().setStatus(httpResponseCode);
            }
        });
        server.setHandler(collector);
        server.start();
    }

    private Request testRequest(int responseCode, String httpMethod) throws Exception {
        HttpChannel channel = new HttpChannel(connector, new HttpConfiguration(), null, new DummyTransport());
        MetaData.Request metaData = new MetaData.Request(httpMethod, new HttpURI("http://foo/bar"), HttpVersion.HTTP_1_1, new HttpFields());
        Request req = channel.getRequest();
        req.setMetaData(metaData);

        this.httpResponseCode = responseCode;
        channel.handle();
        return req;
    }

    private final class DummyTransport implements HttpTransport {
        @Override
        public void send(Response info, boolean head, ByteBuffer content, boolean lastContent, Callback callback) {
            callback.succeeded();
        }

        @Override
        public boolean isPushSupported() {
            return false;
        }

        @Override
        public boolean isOptimizedForDirectBuffers() {
            return false;
        }

        @Override
        public void push(MetaData.Request request) {
        }

        @Override
        public void onCompleted() {
        }

        @Override
        public void abort(Throwable failure) {
        }
    }
}
