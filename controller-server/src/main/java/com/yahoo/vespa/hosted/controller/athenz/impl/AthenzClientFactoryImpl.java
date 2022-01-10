// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.client.ErrorHandler;
import com.yahoo.vespa.athenz.client.zms.DefaultZmsClient;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * @author bjorncs
 */
public class AthenzClientFactoryImpl implements AthenzClientFactory {

    private static final String METRIC_NAME = "athenz.request.error";
    private static final String ATHENZ_SERVICE_DIMENSION = "athenz-service";
    private static final String EXCEPTION_DIMENSION = "exception";

    private final AthenzConfig config;
    private final ServiceIdentityProvider identityProvider;
    private final Metric metrics;
    private final Map<String, Metric.Context> metricContexts;

    @Inject
    public AthenzClientFactoryImpl(ServiceIdentityProvider identityProvider, AthenzConfig config, Metric metrics) {
        this.identityProvider = identityProvider;
        this.config = config;
        this.metrics = metrics;
        this.metricContexts = new HashMap<>();
    }

    @Override
    public AthenzIdentity getControllerIdentity() {
        return identityProvider.identity();
    }

    /**
     * @return A ZMS client instance with the service identity as principal.
     */
    @Override
    public ZmsClient createZmsClient() {
        return new DefaultZmsClient(URI.create(config.zmsUrl()), identityProvider, this::reportMetricErrorHandler);
    }

    /**
     * @return A ZTS client instance with the service identity as principal.
     */
    @Override
    public ZtsClient createZtsClient() {
        return new DefaultZtsClient.Builder(URI.create(config.ztsUrl())).withIdentityProvider(identityProvider).build();
    }

    @Override
    public boolean cacheLookups() {
        return true;
    }

    private void reportMetricErrorHandler(ErrorHandler.RequestProperties request, Exception error) {
        Metric.Context context = metricContexts.computeIfAbsent(request.hostname(), host -> metrics.createContext(
                Map.of(ATHENZ_SERVICE_DIMENSION, host,
                        EXCEPTION_DIMENSION, error.getClass().getSimpleName())));
        metrics.add(METRIC_NAME, 1, context);
    }
}
