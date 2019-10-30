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

import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;

/**
 * Generates metrics values in json format for the metrics/v1 rest api.
 *
 * @author gjoranv
 */
public class ValuesFetcher {
    private static final Logger log = Logger.getLogger(ValuesFetcher.class.getName());

    public static final ConsumerId DEFAULT_PUBLIC_CONSUMER_ID = toConsumerId("default");

    private final MetricsManager metricsManager;
    private final VespaServices vespaServices;
    private final MetricsConsumers metricsConsumers;

    public ValuesFetcher(MetricsManager metricsManager,
                  VespaServices vespaServices,
                  MetricsConsumers metricsConsumers) {
        this.metricsManager = metricsManager;
        this.vespaServices = vespaServices;
        this.metricsConsumers = metricsConsumers;
    }

    public List<MetricsPacket> fetch(String requestedConsumer) throws JsonRenderingException {
        ConsumerId consumer = getConsumerOrDefault(requestedConsumer);

        return metricsManager.getMetrics(vespaServices.getVespaServices(), Instant.now())
                .stream()
                .filter(metricsPacket -> metricsPacket.consumers().contains(consumer))
                .collect(Collectors.toList());
    }

    public List<MetricsPacket> fetchAllMetrics() throws JsonRenderingException {
        return metricsManager.getMetrics(vespaServices.getVespaServices(), Instant.now());
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

}
