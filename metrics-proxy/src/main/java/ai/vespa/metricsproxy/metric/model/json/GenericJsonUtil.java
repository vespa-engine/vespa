// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.http.application.Node;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import ai.vespa.metricsproxy.metric.model.StatusCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.metric.ExternalMetrics.VESPA_NODE_SERVICE_ID;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.json.JacksonUtil.createObjectMapper;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;

/**
 * Utilities for converting between metrics packets and the generic json format.
 *
 * @author gjoranv
 */
public class GenericJsonUtil {
    private static final Logger log = Logger.getLogger(GenericJsonUtil.class.getName());

    private GenericJsonUtil() {
    }

    public static GenericApplicationModel toGenericApplicationModel(Map<Node, List<MetricsPacket>> metricsByNode) {
        var applicationModel = new GenericApplicationModel();

        var genericJsonModels = new ArrayList<GenericJsonModel>();
        metricsByNode.forEach(
                (node, metrics) -> genericJsonModels.add(toGenericJsonModel(metrics, node)));

        applicationModel.nodes = genericJsonModels;
        return applicationModel;
    }

    public static GenericJsonModel toGenericJsonModel(List<MetricsPacket> metricsPackets) {
        return toGenericJsonModel(metricsPackets, null);
    }

    public static GenericJsonModel toGenericJsonModel(List<MetricsPacket> metricsPackets, Node node) {
        Map<ServiceId, List<MetricsPacket>> packetsByService = metricsPackets.stream()
                .collect(Collectors.groupingBy(packet -> packet.service, LinkedHashMap::new, toList()));

        var jsonModel = new GenericJsonModel();
        if (node != null) {
            jsonModel.hostname = node.hostname;
            jsonModel.role = node.role;
        }

        var genericServices = new ArrayList<GenericService>();
        packetsByService.forEach((serviceId, packets) -> {
            var genericMetricsList = packets.stream()
                    .filter(packet -> ! (packet.metrics().isEmpty() && packet.dimensions().isEmpty()))
                    .map(packet -> new GenericMetrics(packet.metrics(), packet.dimensions()))
                    .collect(toList());
            var genericService = packets.stream().findFirst()
                    .map(firstPacket -> new GenericService(serviceId.id,
                                                           firstPacket.timestamp,
                                                           StatusCode.values()[firstPacket.statusCode],
                                                           firstPacket.statusMessage,
                                                           genericMetricsList))
                    .get();
            if (VESPA_NODE_SERVICE_ID.equals(serviceId)) {
                jsonModel.node = new GenericNode(genericService.timestamp, genericService.metrics);
            } else {
                genericServices.add(genericService);

            }
        });

        jsonModel.services = genericServices;
        return jsonModel;
    }

    public static List<MetricsPacket.Builder> toMetricsPackets(String jsonString) {
        try {
            ObjectMapper mapper = createObjectMapper();
            GenericJsonModel jsonModel = mapper.readValue(jsonString, GenericJsonModel.class);
            return toMetricsPackets(jsonModel);
        } catch (IOException e) {
            log.log(WARNING, "Could not create metrics packet from string:\n" + jsonString, e);
            return emptyList();
        }
    }

    public static List<MetricsPacket.Builder> toMetricsPackets(GenericJsonModel jsonModel) {
        var packets = toNodePackets(jsonModel.node);
        jsonModel.services.forEach(genericService -> packets.addAll(toServicePackets(genericService)));

        return packets;
    }

    private static List<MetricsPacket.Builder> toNodePackets(GenericNode node) {
        List<MetricsPacket.Builder> packets = new ArrayList<>();
        if (node == null) return packets;

        if (node.metrics == null || node.metrics.isEmpty()) {
            return singletonList(new MetricsPacket.Builder(VESPA_NODE_SERVICE_ID)
                                         .statusCode(StatusCode.UP.ordinal())
                                         .timestamp(node.timestamp));
        }

        for (var genericMetrics : node.metrics) {
            var packet = new MetricsPacket.Builder(VESPA_NODE_SERVICE_ID)
                    .statusCode(StatusCode.UP.ordinal())
                    .timestamp(node.timestamp);
            addMetrics(genericMetrics, packet);
            packets.add(packet);
        }
        return packets;
    }

    private static List<MetricsPacket.Builder> toServicePackets(GenericService service) {
        List<MetricsPacket.Builder> packets = new ArrayList<>();
        if (service.metrics == null || service.metrics.isEmpty())
            return singletonList(newServicePacket(service));

        for (var genericMetrics : service.metrics) {
            var packet = newServicePacket(service);
            addMetrics(genericMetrics, packet);
            packets.add(packet);
        }
        return packets;

    }

    private static MetricsPacket.Builder newServicePacket(GenericService service) {
        return new MetricsPacket.Builder(ServiceId.toServiceId(service.name))
                .statusCode(StatusCode.fromString(service.status.code).ordinal())
                .statusMessage(service.status.description)
                .timestamp(service.timestamp);
    }

    private static void addMetrics(GenericMetrics genericMetrics, MetricsPacket.Builder packet) {
        genericMetrics.values.forEach((id, value) -> packet.putMetric(toMetricId(id), value));
        genericMetrics.dimensions.forEach((id, value) -> packet.putDimension(toDimensionId(id), value));
    }

}
