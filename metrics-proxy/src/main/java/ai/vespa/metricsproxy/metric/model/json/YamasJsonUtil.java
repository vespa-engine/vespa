// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.http.ValuesFetcher.defaultMetricsConsumerId;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.WARNING;

/**
 * @author gjoranv
 */
public class YamasJsonUtil {

    private static final Logger log = Logger.getLogger(YamasJsonUtil.class.getName());
    private static final JsonFactory factory = JsonFactory.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    static final String YAMAS_ROUTING = "yamas";

    public static MetricsPacket.Builder toMetricsPacketBuilder(YamasJsonModel jsonModel) {
        if (jsonModel.application == null)
            throw new IllegalArgumentException("Service id cannot be null");

        return new MetricsPacket.Builder(ServiceId.toServiceId(jsonModel.application))
                .statusCode(jsonModel.status_code)
                .statusMessage(jsonModel.status_msg)
                .timestamp(jsonModel.timestamp)
                .putMetrics(jsonModel.getMetricsList())
                .putDimensions(jsonModel.getDimensionsById())
                .addConsumers(jsonModel.getYamasConsumers());
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

    public static List<MetricsPacket> appendOptionalStatusPacket(List<MetricsPacket> packets) {
        if (packets.isEmpty()) return packets;

        Set<ConsumerId> consumers = extractSetForRouting(packets.get(0).consumers());
        if (consumers.isEmpty()) return packets;
        List<MetricsPacket> withStatus = new ArrayList<>(packets);
        withStatus.add(new MetricsPacket.Builder(ServiceId.toServiceId("yms_check_vespa"))
                .statusCode(0)
                .statusMessage("Data collected successfully")
                .addConsumers(consumers).build());
        return withStatus;
    }

    public static String toJson(List<MetricsPacket> metrics, boolean addStatus) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            toJson(metrics, output, addStatus);
            output.flush();
            return output.toString();
        } catch (IOException e) {
            return "{}";
        }
    }
    private static Set<ConsumerId> extractSetForRouting(Set<ConsumerId> consumers) {
        return consumers.stream()
                .filter(consumerId -> consumerId != defaultMetricsConsumerId)
                .collect(Collectors.toSet());
    }
    public static void toJson(List<MetricsPacket> metrics, OutputStream outputStream, boolean addStatus) throws IOException {
        JsonGenerator generator = factory.createGenerator(outputStream);
        generator.writeStartObject();
        if (metrics.isEmpty()) {
            generator.writeEndObject();
            return;
        }
        generator.writeArrayFieldStart("metrics");
        for (int i = 0; i < metrics.size() - 1; i++) {
            toJson(metrics.get(i), generator, addStatus);
        }
        toJson(metrics.get(metrics.size() - 1), generator, addStatus);
        generator.writeEndArray();
        generator.writeEndObject();
        generator.close();
    }

    private static void toJson(MetricsPacket metric, JsonGenerator generator, boolean addStatus) throws IOException {
        generator.writeStartObject();
        if (addStatus) {
            generator.writeNumberField("status_code", metric.statusCode);
        }
        if (metric.timestamp != 0) {
            generator.writeNumberField("timestamp", metric.timestamp);
        }
        generator.writeStringField("application", metric.service.id);

        if ( ! metric.metrics().isEmpty()) {
            generator.writeObjectFieldStart("metrics");
            for (var m : metric.metrics().entrySet()) {
                generator.writeFieldName(m.getKey().id);
                JacksonUtil.writeDouble(generator, m.getValue().doubleValue());
            }
            generator.writeEndObject();
        }

        if ( ! metric.dimensions().isEmpty()) {
            generator.writeObjectFieldStart("dimensions");
            for (var m : metric.dimensions().entrySet()) {
                generator.writeStringField(m.getKey().id, m.getValue());
            }
            generator.writeEndObject();
        }
        Set<ConsumerId> routing = extractSetForRouting(metric.consumers());
        if (!routing.isEmpty()) {
            generator.writeObjectFieldStart("routing");
            generator.writeObjectFieldStart(YAMAS_ROUTING);
            generator.writeArrayFieldStart("namespaces");
            for (ConsumerId consumer : routing) {
                generator.writeString(consumer.id);
            }
            generator.writeEndArray();
            generator.writeEndObject();
            generator.writeEndObject();
        }
        if (addStatus) {
            generator.writeStringField("status_msg", metric.statusMessage);
        }
        generator.writeEndObject();
    }
}
