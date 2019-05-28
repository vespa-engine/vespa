/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.metric.ExternalMetrics.VESPA_NODE_SERVICE_ID;
import static java.util.stream.Collectors.toList;

/**
 * Utilities for converting between metrics packets and the generic json format.
 *
 * @author gjoranv
 */
public class GenericJsonUtil {

    private GenericJsonUtil() { }

    public static GenericJsonModel toGenericJsonModel(List<MetricsPacket> metricsPackets) {
        Map<ServiceId, List<MetricsPacket>> packetsByService = metricsPackets.stream()
                .collect(Collectors.groupingBy(packet -> packet.service));

        var jsonModel = new GenericJsonModel();
        var genericServices = new ArrayList<GenericService>();
        packetsByService.forEach((serviceId, packets) -> {
            var genericMetricsList = packets.stream()
                    .map(packet -> new GenericMetrics(packet.metrics(), packet.dimensions()))
                    .collect(toList());
            var genericService = new GenericService(serviceId.id,
                                                    packets.get(0).timestamp,
                                                    genericMetricsList);
            if (VESPA_NODE_SERVICE_ID.equals(serviceId)) {
                jsonModel.node = new GenericNode(genericService.timestamp, genericService.metrics);
            } else {
                genericServices.add(genericService);

            }
        });

        jsonModel.services = genericServices;
        return jsonModel;
    }

}
