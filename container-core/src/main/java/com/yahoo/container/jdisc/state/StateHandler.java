// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.google.inject.Inject;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.Vtag;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.metrics.MetricsPresentationConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;

/**
 * A handler which returns state (health) information from this container instance: Status, metrics and vespa version.
 *
 * @author Simon Thoresen Hult
 */
public class StateHandler extends AbstractRequestHandler {

    public static final String STATE_API_ROOT = "/state/v1";
    private static final String METRICS_PATH = "metrics";
    private static final String HISTOGRAMS_PATH = "metrics/histograms";
    private static final String CONFIG_GENERATION_PATH = "config";
    private static final String HEALTH_PATH = "health";
    private static final String VERSION_PATH = "version";
    
    private final static MetricDimensions NULL_DIMENSIONS = StateMetricContext.newInstance(null);
    private final StateMonitor monitor;
    private final Timer timer;
    private final byte[] config;
    private final SnapshotProvider snapshotPreprocessor;

    @Inject
    public StateHandler(StateMonitor monitor, Timer timer, ApplicationMetadataConfig config,
                        ComponentRegistry<SnapshotProvider> preprocessors, MetricsPresentationConfig presentation) {
        this.monitor = monitor;
        this.timer = timer;
        this.config = buildConfigOutput(config);
        snapshotPreprocessor = getSnapshotPreprocessor(preprocessors, presentation);
    }

    static SnapshotProvider getSnapshotPreprocessor(ComponentRegistry<SnapshotProvider> preprocessors, MetricsPresentationConfig presentation) {
        List<SnapshotProvider> allPreprocessors = preprocessors.allComponents();
        if (presentation.slidingwindow() && allPreprocessors.size() > 0) {
            return allPreprocessors.get(0);
        } else {
            return null;
        }
    }

    @Override
    public ContentChannel handleRequest(final Request request, ResponseHandler handler) {
        new ResponseDispatch() {

            @Override
            protected Response newResponse() {
                Response response = new Response(Response.Status.OK);
                response.headers().add(HttpHeaders.Names.CONTENT_TYPE, resolveContentType(request.getUri()));
                return response;
            }

            @Override
            protected Iterable<ByteBuffer> responseContent() {
                return Collections.singleton(buildContent(request.getUri()));
            }
        }.dispatch(handler);
        return null;
    }

    private String resolveContentType(URI requestUri) {
        if (resolvePath(requestUri).equals(HISTOGRAMS_PATH)) {
            return "text/plain; charset=utf-8";
        } else {
            return "application/json";
        }
    }

    private ByteBuffer buildContent(URI requestUri) {
        String suffix = resolvePath(requestUri);
        switch (suffix) {
            case "":
                return ByteBuffer.wrap(apiLinks(requestUri));
            case CONFIG_GENERATION_PATH:
                return ByteBuffer.wrap(config);
            case HISTOGRAMS_PATH:
                return ByteBuffer.wrap(buildHistogramsOutput());
            case HEALTH_PATH:
            case METRICS_PATH:
                return ByteBuffer.wrap(buildMetricOutput(suffix));
            case VERSION_PATH:
                return ByteBuffer.wrap(buildVersionOutput());
            default:
                // XXX should possibly do something else here
                return ByteBuffer.wrap(buildMetricOutput(suffix));
        }
    }

    private byte[] apiLinks(URI requestUri) {
        try {
            int port = requestUri.getPort();
            String host = requestUri.getHost();
            StringBuilder base = new StringBuilder("http://");
            base.append(host);
            if (port != -1) {
                base.append(":").append(port);
            }
            base.append(STATE_API_ROOT);
            String uriBase = base.toString();
            JSONArray linkList = new JSONArray();
            for (String api : new String[] {METRICS_PATH, CONFIG_GENERATION_PATH, HEALTH_PATH, VERSION_PATH}) {
                JSONObject resource = new JSONObject();
                resource.put("url", uriBase + "/" + api);
                linkList.put(resource);
            }
            return new JSONObjectWithLegibleException()
                    .put("resources", linkList)
                    .toString(4).getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new RuntimeException("Bad JSON construction.", e);
        }
    }

    private static String resolvePath(URI uri) {
        String path = uri.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.startsWith(STATE_API_ROOT)) {
            path = path.substring(STATE_API_ROOT.length());
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private static byte[] buildConfigOutput(ApplicationMetadataConfig config) {
        try {
            return new JSONObjectWithLegibleException()
                    .put(CONFIG_GENERATION_PATH, new JSONObjectWithLegibleException()
                            .put("generation", config.generation())
                            .put("container", new JSONObjectWithLegibleException()
                                    .put("generation", config.generation())))
                    .toString(4).getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new RuntimeException("Bad JSON construction.", e);
        }
    }

    private static byte[] buildVersionOutput() {
        try {
            return new JSONObjectWithLegibleException()
                    .put("version", Vtag.currentVersion)
                    .toString(4).getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new RuntimeException("Bad JSON construction.", e);
        }
    }

    private byte[] buildMetricOutput(String consumer) {
        try {
            return buildJsonForConsumer(consumer).toString(4).getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new RuntimeException("Bad JSON construction.", e);
        }
    }

    private byte[] buildHistogramsOutput() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (snapshotPreprocessor != null) {
            snapshotPreprocessor.histogram(new PrintStream(baos));
        }
        return baos.toByteArray();
    }

    private JSONObjectWithLegibleException buildJsonForConsumer(String consumer) throws JSONException {
        JSONObjectWithLegibleException ret = new JSONObjectWithLegibleException();
        ret.put("time", timer.currentTimeMillis());
        ret.put("status", new JSONObjectWithLegibleException().put("code", getStatus().name()));
        ret.put(METRICS_PATH, buildJsonForSnapshot(consumer, getSnapshot()));
        return ret;
    }

    private MetricSnapshot getSnapshot() {
        if (snapshotPreprocessor == null) {
            return monitor.snapshot();
        } else {
            return snapshotPreprocessor.latestSnapshot();
        }
    }

    private StateMonitor.Status getStatus() {
        return monitor.status();
    }

    private JSONObjectWithLegibleException buildJsonForSnapshot(String consumer, MetricSnapshot metricSnapshot) throws JSONException {
        if (metricSnapshot == null) {
            return new JSONObjectWithLegibleException();
        }
        JSONObjectWithLegibleException jsonMetric = new JSONObjectWithLegibleException();
        jsonMetric.put("snapshot", new JSONObjectWithLegibleException()
                .put("from", metricSnapshot.getFromTime(TimeUnit.MILLISECONDS) / 1000.0)
                .put("to", metricSnapshot.getToTime(TimeUnit.MILLISECONDS) / 1000.0));

        boolean includeDimensions = !consumer.equals(HEALTH_PATH);
        long periodInMillis = metricSnapshot.getToTime(TimeUnit.MILLISECONDS) -
                              metricSnapshot.getFromTime(TimeUnit.MILLISECONDS);
        for (Tuple tuple : collapseMetrics(metricSnapshot, consumer)) {
            JSONObjectWithLegibleException jsonTuple = new JSONObjectWithLegibleException();
            jsonTuple.put("name", tuple.key);
            if (tuple.val instanceof CountMetric) {
                CountMetric count = (CountMetric)tuple.val;
                jsonTuple.put("values", new JSONObjectWithLegibleException()
                        .put("count", count.getCount())
                        .put("rate", (count.getCount() * 1000.0) / periodInMillis));
            } else if (tuple.val instanceof GaugeMetric) {
                GaugeMetric gauge = (GaugeMetric) tuple.val;
                JSONObjectWithLegibleException valueFields = new JSONObjectWithLegibleException();
                valueFields.put("average", gauge.getAverage())
                        .put("sum", gauge.getSum())
                        .put("count", gauge.getCount())
                        .put("last", gauge.getLast())
                        .put("max", gauge.getMax())
                        .put("min", gauge.getMin())
                        .put("rate", (gauge.getCount() * 1000.0) / periodInMillis);
                if (gauge.getPercentiles().isPresent()) {
                    for (Tuple2<String, Double> prefixAndValue : gauge.getPercentiles().get()) {
                        valueFields.put(prefixAndValue.first + "percentile", prefixAndValue.second.doubleValue());
                    }
                }
                jsonTuple.put("values", valueFields);
            } else {
                throw new UnsupportedOperationException(tuple.val.getClass().getName());
            }
            Iterator<Map.Entry<String, String>> it = tuple.dim.iterator();
            if (it.hasNext() && includeDimensions) {
                JSONObjectWithLegibleException jsonDim = new JSONObjectWithLegibleException();
                while (it.hasNext()) {
                    Map.Entry<String, String> entry = it.next();
                    jsonDim.put(entry.getKey(), entry.getValue());
                }
                jsonTuple.put("dimensions", jsonDim);
            }
            jsonMetric.append("values", jsonTuple);
        }
        return jsonMetric;
    }

    private static List<Tuple> collapseMetrics(MetricSnapshot snapshot, String consumer) {
        switch (consumer) {
            case HEALTH_PATH:
                return collapseHealthMetrics(snapshot);
            case "all": // deprecated name
            case METRICS_PATH:
                return flattenAllMetrics(snapshot);
            default:
                throw new IllegalArgumentException("Unknown consumer '" + consumer + "'.");
        }
    }

    private static List<Tuple> collapseHealthMetrics(MetricSnapshot snapshot) {
        Tuple requestsPerSecond = new Tuple(NULL_DIMENSIONS, "requestsPerSecond", null);
        Tuple latencySeconds = new Tuple(NULL_DIMENSIONS, "latencySeconds", null);
        for (Map.Entry<MetricDimensions, MetricSet> entry : snapshot) {
            MetricSet metricSet = entry.getValue();
            MetricValue val = metricSet.get("serverTotalSuccessfulResponseLatency");
            if (val instanceof GaugeMetric) {
                GaugeMetric gauge = (GaugeMetric)val;
                latencySeconds.add(GaugeMetric.newInstance(gauge.getLast() / 1000,
                                                           gauge.getMax() / 1000,
                                                           gauge.getMin() / 1000,
                                                           gauge.getSum() / 1000,
                                                           gauge.getCount()));
            }
            requestsPerSecond.add(metricSet.get("serverNumSuccessfulResponses"));
        }
        List<Tuple> lst = new ArrayList<>();
        if (requestsPerSecond.val != null) {
            lst.add(requestsPerSecond);
        }
        if (latencySeconds.val != null) {
            lst.add(latencySeconds);
        }
        return lst;
    }

    /** Produces a flat list of metric entries from a snapshot (which organizes metrics by dimensions) */
    static List<Tuple> flattenAllMetrics(MetricSnapshot snapshot) {
        List<Tuple> metrics = new ArrayList<>();
        for (Map.Entry<MetricDimensions, MetricSet> snapshotEntry : snapshot) {
            for (Map.Entry<String, MetricValue> metricSetEntry : snapshotEntry.getValue()) {
                metrics.add(new Tuple(snapshotEntry.getKey(), metricSetEntry.getKey(), metricSetEntry.getValue()));
            }
        }
        return metrics;
    }

    static class Tuple {

        final MetricDimensions dim;
        final String key;
        MetricValue val;

        Tuple(MetricDimensions dim, String key, MetricValue val) {
            this.dim = dim;
            this.key = key;
            this.val = val;
        }

        void add(MetricValue val) {
            if (val == null) {
                return;
            }
            if (this.val == null) {
                this.val = val;
            } else {
                this.val.add(val);
            }
        }
    }

}
