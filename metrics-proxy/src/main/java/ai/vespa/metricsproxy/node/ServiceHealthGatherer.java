// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.node;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.StatusCode;
import ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil;
import ai.vespa.metricsproxy.service.VespaServices;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.node.NodeMetricGatherer.ROUTING_JSON;

/**
 * @author olaa
 */
public class ServiceHealthGatherer {


    protected static List<MetricsPacket.Builder> gatherServiceHealthMetrics(VespaServices vespaServices)  {
        return vespaServices.getVespaServices()
                .stream()
                .map(service -> {
                    try {
                        StatusCode healthStatus = service.getHealth().getStatus();
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("status_code", healthStatus.code);
                        jsonObject.put("status_message", healthStatus.status);
                        jsonObject.put("application", service.getMonitoringName());
                        JSONObject dimensions = new JSONObject();
                        dimensions.put("instance", service.getInstanceName());
                        dimensions.put("metrictype", "health");
                        jsonObject.put("dimensions", dimensions);
                        jsonObject.put("routing", ROUTING_JSON);
                        return YamasJsonUtil.toMetricsPackets(jsonObject.toString()).get(0);
                    } catch (JSONException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                })
                .collect(Collectors.toList());
    }
}
