// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.Vtag;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.jdisc.RequestView;
import com.yahoo.container.jdisc.utils.CapabilityRequiringRequestHandler;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.security.tls.Capability;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.yahoo.container.jdisc.state.JsonUtil.sanitizeDouble;

/**
 * A handler which returns state (health) information from this container instance: Status, metrics and vespa version.
 *
 * @author Simon Thoresen Hult
 */
public class StateHandler extends AbstractRequestHandler implements CapabilityRequiringRequestHandler {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public static final String STATE_API_ROOT = "/state/v1";
    private static final String METRICS_PATH = "metrics";
    private static final String HISTOGRAMS_PATH = "metrics/histograms";
    private static final String CONFIG_GENERATION_PATH = "config";
    private static final String HEALTH_PATH = "health";
    private static final String VERSION_PATH = "version";

    private final static MetricDimensions NULL_DIMENSIONS = StateMetricContext.newInstance(null);
    private final StateMonitor monitor;
    private final Timer timer;
    private final JsonNode config;
    private final SnapshotProvider snapshotProvider;

    @Inject
    public StateHandler(StateMonitor monitor, Timer timer, ApplicationMetadataConfig config,
                        ComponentRegistry<SnapshotProvider> snapshotProviders) {
        this.monitor = monitor;
        this.timer = timer;
        this.config = buildConfigJson(config);
        snapshotProvider = getSnapshotProviderOrThrow(snapshotProviders);
    }

    @Override public Capability requiredCapability(RequestView __) { return Capability.CONTAINER__STATE_API; }

    static SnapshotProvider getSnapshotProviderOrThrow(ComponentRegistry<SnapshotProvider> preprocessors) {
        List<SnapshotProvider> allPreprocessors = preprocessors.allComponents();
        if (allPreprocessors.size() > 0) {
            return allPreprocessors.get(0);
        } else {
            throw new IllegalArgumentException("At least one snapshot provider is required.");
        }
    }


    private static class MyContentChannel implements ContentChannel {
        private final List<ByteBuffer> buffers;
        private final Runnable trigger;
        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            buffers.add(buf);
            if (handler != null) handler.completed();
        }
        @Override
        public void close(CompletionHandler handler) {
            trigger.run();
            if (handler != null) handler.completed();
        }
        MyContentChannel(List<ByteBuffer> buffers, Runnable trigger) {
            this.buffers = buffers;
            this.trigger = trigger;
        }
    }

    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler handler) {
        List<ByteBuffer> input = new ArrayList<>();
        var respDisp = new ResponseDispatch() {
            @Override
            protected Response newResponse() {
                Response response = new Response(Response.Status.OK);
                response.headers().add(HttpHeaders.Names.CONTENT_TYPE, resolveContentType(request.getUri()));
                return response;
            }

            @Override
            protected Iterable<ByteBuffer> responseContent() {
                return Collections.singleton(buildContent(request.getUri(), input));
            }
        };
        return new MyContentChannel(input, () -> { respDisp.dispatch(handler); });
    }

    private String resolveContentType(URI requestUri) {
        if (resolvePath(requestUri).equals(HISTOGRAMS_PATH)) {
            return "text/plain; charset=utf-8";
        } else {
            return "application/json";
        }
    }

    private ByteBuffer buildContent(URI requestUri, List<ByteBuffer> input) {
        try {
            String suffix = resolvePath(requestUri);
            return switch (suffix) {
            case "" -> ByteBuffer.wrap(apiLinks(requestUri));
            case CONFIG_GENERATION_PATH -> ByteBuffer.wrap(toPrettyString(config));
            case HISTOGRAMS_PATH -> ByteBuffer.wrap(buildHistogramsOutput());
            case HEALTH_PATH, METRICS_PATH -> ByteBuffer.wrap(buildMetricOutput(suffix));
            case VERSION_PATH -> ByteBuffer.wrap(buildVersionOutput());
            default -> ByteBuffer.wrap(buildMetricOutput(suffix)); // XXX should possibly do something else here
            };
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Bad JSON construction", e);
        }
    }

    private byte[] apiLinks(URI requestUri) throws JsonProcessingException {
        int port = requestUri.getPort();
        String host = requestUri.getHost();
        StringBuilder base = new StringBuilder("http://");
        base.append(host);
        if (port != -1) {
            base.append(":").append(port);
        }
        base.append(STATE_API_ROOT);
        String uriBase = base.toString();
        ArrayNode linkList = jsonMapper.createArrayNode();
        for (String api : new String[] {METRICS_PATH, CONFIG_GENERATION_PATH, HEALTH_PATH, VERSION_PATH}) {
            ObjectNode resource = jsonMapper.createObjectNode();
            resource.put("url", uriBase + "/" + api);
            linkList.add(resource);
        }
        JsonNode resources = jsonMapper.createObjectNode().set("resources", linkList);
        return toPrettyString(resources);
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

    private static JsonNode buildConfigJson(ApplicationMetadataConfig config) {
        return jsonMapper.createObjectNode()
                .set(CONFIG_GENERATION_PATH, jsonMapper.createObjectNode()
                     .put("generation", config.generation())
                     .set("container", jsonMapper.createObjectNode()
                          .put("generation", config.generation())));
    }

    private static byte[] buildVersionOutput() throws JsonProcessingException {
        return toPrettyString(
                jsonMapper.createObjectNode()
                .put("version", Vtag.currentVersion.toString()));
    }

    private byte[] buildMetricOutput(String consumer) throws JsonProcessingException {
        return toPrettyString(buildJsonForConsumer(consumer));
    }

    private byte[] buildHistogramsOutput() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (snapshotProvider != null) {
            snapshotProvider.histogram(new PrintStream(baos));
        }
        return baos.toByteArray();
    }

    private ObjectNode buildJsonForConsumer(String consumer) {
        ObjectNode ret = jsonMapper.createObjectNode();
        ret.put("time", timer.currentTimeMillis());
        ret.set("status", jsonMapper.createObjectNode().put("code", getStatus().name()));
        ret.set(METRICS_PATH, buildJsonForSnapshot(consumer, getSnapshot()));
        return ret;
    }

    private MetricSnapshot getSnapshot() {
        return snapshotProvider.latestSnapshot();
    }

    private StateMonitor.Status getStatus() {
        return monitor.status();
    }

    private ObjectNode buildJsonForSnapshot(String consumer, MetricSnapshot metricSnapshot) {
        if (metricSnapshot == null) {
            return jsonMapper.createObjectNode();
        }
        ObjectNode jsonMetric = jsonMapper.createObjectNode();
        jsonMetric.set("snapshot", jsonMapper.createObjectNode()
                .put("from", sanitizeDouble(metricSnapshot.getFromTime(TimeUnit.MILLISECONDS) / 1000.0))
                .put("to", sanitizeDouble(metricSnapshot.getToTime(TimeUnit.MILLISECONDS) / 1000.0)));

        boolean includeDimensions = !consumer.equals(HEALTH_PATH);
        long periodInMillis = metricSnapshot.getToTime(TimeUnit.MILLISECONDS) -
                              metricSnapshot.getFromTime(TimeUnit.MILLISECONDS);
        for (Tuple tuple : collapseMetrics(metricSnapshot, consumer)) {
            ObjectNode jsonTuple = jsonMapper.createObjectNode();
            jsonTuple.put("name", tuple.key);
            if (tuple.val instanceof CountMetric count) {
                jsonTuple.set("values", jsonMapper.createObjectNode()
                        .put("count", count.getCount())
                        .put("rate", sanitizeDouble(count.getCount() * 1000.0) / periodInMillis));
            } else if (tuple.val instanceof GaugeMetric gauge) {
                ObjectNode valueFields = jsonMapper.createObjectNode();
                valueFields.put("average", sanitizeDouble(gauge.getAverage()))
                        .put("sum", sanitizeDouble(gauge.getSum()))
                        .put("count", gauge.getCount())
                        .put("last", sanitizeDouble(gauge.getLast()))
                        .put("max", sanitizeDouble(gauge.getMax()))
                        .put("min", sanitizeDouble(gauge.getMin()))
                        .put("rate", sanitizeDouble((gauge.getCount() * 1000.0) / periodInMillis));
                if (gauge.getPercentiles().isPresent()) {
                    for (Tuple2<String, Double> prefixAndValue : gauge.getPercentiles().get()) {
                        valueFields.put(prefixAndValue.first + "percentile", sanitizeDouble(prefixAndValue.second));
                    }
                }
                jsonTuple.set("values", valueFields);
            } else {
                throw new UnsupportedOperationException(tuple.val.getClass().getName());
            }
            if (tuple.dim != null) {
                Iterator<Map.Entry<String, String>> it = tuple.dim.iterator();
                if (it.hasNext() && includeDimensions) {
                    ObjectNode jsonDim = jsonMapper.createObjectNode();
                    while (it.hasNext()) {
                        Map.Entry<String, String> entry = it.next();
                        jsonDim.put(entry.getKey(), entry.getValue());
                    }
                    jsonTuple.set("dimensions", jsonDim);
                }
            }
            ArrayNode values = (ArrayNode) jsonMetric.get("values");
            if (values == null) {
                values = jsonMapper.createArrayNode();
                jsonMetric.set("values", values);
            }
            values.add(jsonTuple);
        }
        return jsonMetric;
    }

    private static List<Tuple> collapseMetrics(MetricSnapshot snapshot, String consumer) {
        return switch (consumer) {
            case HEALTH_PATH -> collapseHealthMetrics(snapshot);
            case "all", METRICS_PATH -> flattenAllMetrics(snapshot);  // TODO: Remove "all" on Vespa 9
            default -> throw new IllegalArgumentException("Unknown consumer '" + consumer + "'.");
        };
    }

    private static List<Tuple> collapseHealthMetrics(MetricSnapshot snapshot) {
        Tuple requestsPerSecond = new Tuple(NULL_DIMENSIONS, "requestsPerSecond", null);
        Tuple latencySeconds = new Tuple(NULL_DIMENSIONS, "latencySeconds", null);
        for (Map.Entry<MetricDimensions, MetricSet> entry : snapshot) {
            MetricSet metricSet = entry.getValue();
            MetricValue val = metricSet.get("serverTotalSuccessfulResponseLatency");
            if (val instanceof GaugeMetric gauge) {
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

    private static byte[] toPrettyString(JsonNode resources) throws JsonProcessingException {
        return jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(resources)
                .getBytes();
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
