/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http;

import ai.vespa.metricsproxy.core.MetricsConsumers;
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil.toGenericJsonModel;

/**
 * Http handler that exposes the generic metrics format.
 *
 * @author gjoranv
 */
public class GenericMetricsHandler extends ThreadedHttpRequestHandler {
    private static final Logger log = Logger.getLogger(GenericMetricsHandler.class.getName());

    public static final ConsumerId DEFAULT_PUBLIC_CONSUMER_ID = toConsumerId("default-public");

    private final MetricsConsumers metricsConsumers;
    private final MetricsManager metricsManager;
    private final VespaServices vespaServices;

    @Inject
    public GenericMetricsHandler(Executor executor,
                                 MetricsManager metricsManager,
                                 VespaServices vespaServices,
                                 MetricsConsumers metricsConsumers) {
        super(executor);
        this.metricsConsumers = metricsConsumers;
        this.metricsManager = metricsManager;
        this.vespaServices = vespaServices;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            ConsumerId consumer = getConsumerOrDefault(request.getProperty("consumer"));

            List<MetricsPacket> metrics = metricsManager.getMetrics(vespaServices.getVespaServices(), Instant.now())
                    .stream()
                    .filter(metricsPacket -> metricsPacket.consumers().contains(consumer))
                    .collect(Collectors.toList());
            return new Response(200, toGenericJsonModel(metrics).serialize());
        } catch (JsonRenderingException e) {
            return new Response(500, e.getMessageAsJson());
        }
    }

    private ConsumerId getConsumerOrDefault(String consumer) {
        if (consumer == null) return DEFAULT_PUBLIC_CONSUMER_ID;

        ConsumerId consumerId = toConsumerId(consumer);
        if (! metricsConsumers.getAllConsumers().contains(consumerId)) {
            log.info("No consumer with id '" + consumer + "' - using the default consumer instead.");
            return DEFAULT_PUBLIC_CONSUMER_ID;
        }
        return consumerId;
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
