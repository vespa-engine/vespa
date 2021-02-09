// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.communication.ClusterConnection;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

public class OperationStats {

    private static ObjectMapper jsonMapper = createMapper();

    private final String sessionParamsAsXmlString;
    private List<ClusterConnection> clusters;
    private IncompleteResultsThrottler throttler;

    public OperationStats(
            SessionParams sessionParams,
            List<ClusterConnection> clusters,
            IncompleteResultsThrottler throttler) {
        this.sessionParamsAsXmlString = generateSessionParamsAsXmlString(sessionParams);
        this.clusters = clusters;
        this.throttler = throttler;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    private String generateSessionParamsAsXmlString(final SessionParams sessionParams) {
        StringWriter stringWriter = new StringWriter();
        try {
            JsonGenerator jsonGenerator = jsonMapper.createGenerator(stringWriter);
            jsonMapper.writeValue(jsonGenerator, sessionParams); // TODO SessionParams should not be blindly serialized. This may serialize objects that are not really serializable.
            return stringWriter.toString();
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    public String getStatsAsJson() {
        try {
            StringWriter stringWriter = new StringWriter();
            JsonGenerator jsonGenerator = jsonMapper.createGenerator(stringWriter);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeArrayFieldStart("clusters");
            for (ClusterConnection cluster : clusters) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeNumberField("clusterid", cluster.getClusterId());
                jsonGenerator.writeFieldName("stats");
                jsonGenerator.writeRawValue(cluster.getStatsAsJSon());
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
            jsonGenerator.writeFieldName("sessionParams");
            jsonGenerator.writeRawValue(sessionParamsAsXmlString);
            jsonGenerator.writeFieldName("throttleDebugMessage");
            jsonGenerator.writeRawValue("\"" + throttler.getDebugMessage() + "\"");
            jsonGenerator.writeEndObject();
            jsonGenerator.close();
            return stringWriter.toString();
        } catch (IOException e) {
            return "{ \"Error\" : \""+ e.getMessage() + "\"}";
        }
    }

}
