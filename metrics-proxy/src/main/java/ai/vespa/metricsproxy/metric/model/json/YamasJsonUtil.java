// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.http.ValuesFetcher.defaultMetricsConsumerId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static com.yahoo.stream.CustomCollectors.toLinkedMap;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.WARNING;

/**
 * @author gjoranv
 */
public class YamasJsonUtil {
    private static final Logger log = Logger.getLogger(YamasJsonUtil.class.getName());

    static final String YAMAS_ROUTING = "yamas";

    public static MetricsPacket.Builder toMetricsPacketBuilder(YamasJsonModel jsonModel) {
        if (jsonModel.application == null)
            throw new IllegalArgumentException("Service id cannot be null");

        return new MetricsPacket.Builder(toServiceId(jsonModel.application))
                .statusCode(jsonModel.status_code)
                .statusMessage(jsonModel.status_msg)
                .timestamp(jsonModel.timestamp)
                .putMetrics(jsonModel.getMetricsList())
                .putDimensions(jsonModel.getDimensionsById())
                .addConsumers(jsonModel.getYamasConsumers());
    }

    public static YamasArrayJsonModel toYamasArray(Collection<MetricsPacket> metricsPackets) {
        YamasArrayJsonModel yamasArray = toYamasArray(metricsPackets, false);

        // Add a single status object at the end
        yamasArray.metrics.stream().findFirst().map(YamasJsonModel::getYamasConsumers)
                .ifPresent(consumers -> yamasArray.add(getStatusYamasModel("Data collected successfully", 0, consumers)));
        return yamasArray;
    }

    public static YamasArrayJsonModel toYamasArray(Collection<MetricsPacket> metricsPackets, boolean addStatus) {
        YamasArrayJsonModel yamasArray = new YamasArrayJsonModel();
        metricsPackets.forEach(packet -> yamasArray.add(toYamasModel(packet, addStatus)));
        return yamasArray;
    }

    /**
     * Converts the given json formatted string to a list of metrics packet builders.
     * Note that this method returns an empty list if an IOException occurs,
     * and logs a warning as a side effect.
     */
    public static List<MetricsPacket.Builder> toMetricsPackets(String jsonString) {
        List<MetricsPacket.Builder> packets = new ArrayList<>();
        try {
            JsonParser jp = new JsonFactory().createParser(jsonString);
            jp.setCodec(new ObjectMapper());
            while (jp.nextToken() != null) {
                YamasJsonModel jsonModel = jp.readValueAs(YamasJsonModel.class);
                packets.add(toMetricsPacketBuilder(jsonModel));
            }
            return packets;
        } catch (IOException e) {
            log.log(WARNING, "Could not create metrics packet from string:\n" + jsonString, e);
            return emptyList();
        }
    }

    private static YamasJsonModel getStatusYamasModel(String statusMessage, int statusCode, Collection<ConsumerId> consumers) {
        YamasJsonModel model = new YamasJsonModel();
        model.status_code = statusCode;
        model.status_msg = statusMessage;
        model.application = "yms_check_vespa";
        model.routing = ImmutableMap.of(YAMAS_ROUTING, toYamasJsonNamespaces(consumers));
        return model;
    }

    private static YamasJsonModel toYamasModel(MetricsPacket packet, boolean addStatus) {
        YamasJsonModel model = new YamasJsonModel();

        if (addStatus) {
            model.status_code = packet.statusCode;
            model.status_msg = packet.statusMessage;
        }

        model.application = packet.service.id;
        model.timestamp = (packet.timestamp == 0L) ? null : packet.timestamp;

        if (packet.metrics().isEmpty()) model.metrics = null;
        else {
            model.metrics = packet.metrics().entrySet().stream().collect(
                    toLinkedMap(id2metric -> id2metric.getKey().id,
                                id2metric -> id2metric.getValue().doubleValue()));
        }

        if (packet.dimensions().isEmpty()) model.dimensions = null;
        else {
            model.dimensions = packet.dimensions().entrySet()
                    .stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .collect(toLinkedMap(
                            id2dim -> id2dim.getKey().id,
                            Map.Entry::getValue)
                    );
        }

        YamasJsonModel.YamasJsonNamespace namespaces = toYamasJsonNamespaces(packet.consumers());
        if (namespaces.namespaces.isEmpty()) model.routing = null;
        else model.routing = ImmutableMap.of(YAMAS_ROUTING, namespaces);

        return model;
    }

    private static YamasJsonModel.YamasJsonNamespace toYamasJsonNamespaces(Collection<ConsumerId> consumers) {
        YamasJsonModel.YamasJsonNamespace namespaces =  new YamasJsonModel.YamasJsonNamespace();
        namespaces.namespaces = consumers.stream()
                .filter(consumerId -> consumerId != defaultMetricsConsumerId)
                .map(consumer -> consumer.id)
                .collect(Collectors.toList());
        return namespaces;
    }

}
