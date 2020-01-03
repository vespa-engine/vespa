// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;

/**
 * Fetch metrics for a given vespa service
 *
 * @author Jo Kristian Bergum
 */
public class RemoteMetricsFetcher extends HttpMetricFetcher {

    final static String METRICS_PATH = STATE_PATH + "metrics";

    RemoteMetricsFetcher(VespaService service, int port) {
        super(service, port, METRICS_PATH);
    }

    /**
     * Connect to remote service over http and fetch metrics
     */
    public Metrics getMetrics(int fetchCount) {
        String data = "{}";
        try {
            data = getJson();
        } catch (IOException e) {
            logMessageNoResponse(errMsgNoResponse(e), fetchCount);
        }

        return createMetrics(data, fetchCount);
    }

    /**
     * Connect to remote service over http and fetch metrics
     */
    Metrics createMetrics(String data, int fetchCount) {
        Metrics remoteMetrics = new Metrics();
        try {
            remoteMetrics = parse(data);
        } catch (Exception e) {
            handleException(e, data, fetchCount);
        }

        return remoteMetrics;
    }

    private Metrics parse(String data) throws JSONException {
        JSONObject o = new JSONObject(data);
        if (!(o.has("metrics"))) {
            return new Metrics(); //empty
        }

        JSONObject metrics = o.getJSONObject("metrics");
        JSONArray values;
        long timestamp;

        try {
            JSONObject snapshot = metrics.getJSONObject("snapshot");
            timestamp = (long) snapshot.getDouble("to");
            values = metrics.getJSONArray("values");
        } catch (JSONException e) {
            // snapshot might not have been produced. Do not throw exception into log
            return new Metrics();
        }
        long now = System.currentTimeMillis() / 1000;
        timestamp = Metric.adjustTime(timestamp, now);
        Metrics m = new Metrics(timestamp);

        Map<DimensionId, String> noDims = Collections.emptyMap();
        Map<String, Map<DimensionId, String>> uniqueDimensions = new HashMap<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject metric = values.getJSONObject(i);
            String name = metric.getString("name");
            String description = "";

            if (metric.has("description")) {
                description = metric.getString("description");
            }

            Map<DimensionId, String> dim = noDims;
            if (metric.has("dimensions")) {
                JSONObject dimensions = metric.getJSONObject("dimensions");
                StringBuilder sb = new StringBuilder();
                for (Iterator<?> it = dimensions.keys(); it.hasNext(); ) {
                    String k = (String) it.next();
                    String v = dimensions.getString(k);
                    sb.append(toDimensionId(k)).append(v);
                }
                if ( ! uniqueDimensions.containsKey(sb.toString())) {
                    dim = new HashMap<>();
                    for (Iterator<?> it = dimensions.keys(); it.hasNext(); ) {
                        String k = (String) it.next();
                        String v = dimensions.getString(k);
                        dim.put(toDimensionId(k), v);
                    }
                    uniqueDimensions.put(sb.toString(), Collections.unmodifiableMap(dim));
                }
                dim = uniqueDimensions.get(sb.toString());
            }

            JSONObject aggregates = metric.getJSONObject("values");
            for (Iterator<?> it = aggregates.keys(); it.hasNext(); ) {
                String aggregator = (String) it.next();
                Number value = (Number) aggregates.get(aggregator);
                StringBuilder metricName = (new StringBuilder()).append(name).append(".").append(aggregator);
                m.add(new Metric(metricName.toString(), value, timestamp, dim, description));
            }
        }

        return m;
    }
}
