/*
* Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
*/

package ai.vespa.metricsproxy.http;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.gatherer.NodeMetricGatherer;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.json.JsonRenderingException;
import ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil;
import ai.vespa.metricsproxy.service.VespaServices;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.Path;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;

import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static java.util.logging.Level.WARNING;

/**
 * @author olaa
 */

// TODO: Merge with gjoranv's API util changes
public class YamasHandler extends ThreadedHttpRequestHandler {

    static final String V1_PATH = "/yamas/v1";
    static final String VALUES_PATH = V1_PATH + "/values";

    private final ValuesFetcher valuesFetcher;
    private final NodeMetricGatherer nodeMetricGatherer;

    @Inject
    public YamasHandler(Executor executor,
                        MetricsManager metricsManager,
                        VespaServices vespaServices,
                        MetricsConsumers metricsConsumers,
                        ApplicationDimensions applicationDimensions,
                        NodeDimensions nodeDimensions) {
        super(executor);
        valuesFetcher = new ValuesFetcher(metricsManager, vespaServices, metricsConsumers);
        this.nodeMetricGatherer = new NodeMetricGatherer(metricsManager, vespaServices, applicationDimensions, nodeDimensions);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (request.getMethod() != GET) return new JsonResponse(METHOD_NOT_ALLOWED, "Only GET is supported");

        Path path = new Path(request.getUri());

        if (path.matches(V1_PATH)) return v1Response(request.getUri());
        if (path.matches(VALUES_PATH)) return valuesResponse(request);

        return new ErrorResponse(NOT_FOUND, "No content at given path");
    }

    private JsonResponse v1Response(URI requestUri) {
        try {
            return new JsonResponse(OK, v1Content(requestUri));
        } catch (JSONException e) {
            log.log(WARNING, "Bad JSON construction in " + V1_PATH +  " response", e);
            return new ErrorResponse(INTERNAL_SERVER_ERROR, "An error occurred, please try path '" + VALUES_PATH + "'");
        }
    }

    private JsonResponse valuesResponse(HttpRequest request) {
        try {
            List<MetricsPacket> metrics =  valuesFetcher.fetch(request.getProperty("consumer"));
            metrics.addAll(nodeMetricGatherer.gatherMetrics());
            return new JsonResponse(OK, YamasJsonUtil.toYamasArray(metrics).serialize());
        } catch (JsonRenderingException e) {
            return new ErrorResponse(INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    // TODO: Use jackson with a "Resources" class instead of JSONObject
    private String v1Content(URI requestUri) throws JSONException {
        int port = requestUri.getPort();
        String host = requestUri.getHost();
        StringBuilder base = new StringBuilder("http://");
        base.append(host);
        if (port >= 0) {
            base.append(":").append(port);
        }
        String uriBase = base.toString();
        JSONArray linkList = new JSONArray();
        for (String api : new String[] {VALUES_PATH}) {
            JSONObject resource = new JSONObject();
            resource.put("url", uriBase + api);
            linkList.put(resource);
        }
        return new JSONObject().put("resources", linkList).toString(4);
    }
}