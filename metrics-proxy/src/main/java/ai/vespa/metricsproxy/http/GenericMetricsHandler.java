/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http;

import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.JsonRenderingException;
import ai.vespa.metricsproxy.service.VespaServices;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil.toGenericJsonModel;

/**
 * Handler exposing the generic metrics format via http.
 *
 * @author gjoranv
 */
public class GenericMetricsHandler extends ThreadedHttpRequestHandler {

    public static final ConsumerId DEFAULT_PUBLIC_CONSUMER_ID = toConsumerId("default-public");

    private final MetricsManager metricsManager;
    private final VespaServices vespaServices;

    @Inject
    public GenericMetricsHandler(Executor executor, MetricsManager metricsManager, VespaServices vespaServices) {
        super(executor);
        this.metricsManager = metricsManager;
        this.vespaServices = vespaServices;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            List<MetricsPacket> metrics = metricsManager.getMetrics(vespaServices.getVespaServices(), Instant.now());
            return new Response(200, toGenericJsonModel(metrics).serialize());
        } catch (JsonRenderingException e) {
            return new Response(500, e.getMessageAsJson());
        }
    }

    private static class Response extends HttpResponse {
        private final byte[] data;

        Response(int code, String data) {
            super(code);
            this.data = data.getBytes(Charset.forName(DEFAULT_CHARACTER_ENCODING));
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write(data);
        }
    }

}
