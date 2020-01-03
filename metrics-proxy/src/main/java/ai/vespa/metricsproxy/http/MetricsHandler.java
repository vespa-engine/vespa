// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.service.VespaServices;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.Path;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import static ai.vespa.metricsproxy.metric.model.json.GenericJsonUtil.toGenericJsonModel;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.OK;

/**
 * Http handler for the metrics/v1 rest api.
 *
 * @author gjoranv
 */
public class MetricsHandler extends HttpHandlerBase {

    public static final String V1_PATH = "/metrics/v1";
    public static final String VALUES_PATH = V1_PATH + "/values";

    private final ValuesFetcher valuesFetcher;

    @Inject
    public MetricsHandler(Executor executor,
                          MetricsManager metricsManager,
                          VespaServices vespaServices,
                          MetricsConsumers metricsConsumers) {
        super(executor);
        valuesFetcher = new ValuesFetcher(metricsManager, vespaServices, metricsConsumers);
    }

    @Override
    public Optional<HttpResponse> doHandle(URI requestUri, Path apiPath, String consumer) {
        if (apiPath.matches(V1_PATH)) return Optional.of(resourceListResponse(requestUri, List.of(VALUES_PATH)));
        if (apiPath.matches(VALUES_PATH)) return Optional.of(valuesResponse(consumer));
        return Optional.empty();
    }

    private JsonResponse valuesResponse(String consumer) {
        try {
            List<MetricsPacket> metrics =  valuesFetcher.fetch(consumer);
            return new JsonResponse(OK, toGenericJsonModel(metrics).serialize());
        } catch (Exception e) {
            log.log(Level.WARNING, "Got exception when rendering metrics:", e);
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

}
