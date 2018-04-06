// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.communication.ClusterConnection;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

public class OperationStats {

    private static JsonFactory jsonFactory = new JsonFactory();
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

    private String generateSessionParamsAsXmlString(final SessionParams sessionParams) {
        ObjectMapper objectMapper = new ObjectMapper();
        StringWriter stringWriter = new StringWriter();
        try {
            JsonGenerator jsonGenerator = jsonFactory.createGenerator(stringWriter);
            objectMapper.writeValue(jsonGenerator, sessionParams);
            return stringWriter.toString();
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    public String getStatsAsJson() {
        try {
            StringWriter stringWriter = new StringWriter();
            JsonGenerator jsonGenerator = jsonFactory.createGenerator(stringWriter);
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
