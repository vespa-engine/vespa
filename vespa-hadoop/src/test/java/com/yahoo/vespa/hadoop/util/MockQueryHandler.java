// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.util;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockQueryHandler implements HttpHandler {

    private final Map<String, List<MockQueryHit>> hitMap;
    private final String childNode;

    public MockQueryHandler(String childNode) {
        this.hitMap = new HashMap<>();
        this.childNode = childNode;
    }

    public void handle(HttpExchange t) throws IOException {
        URI uri = t.getRequestURI();
        String query = uri.getQuery();
        String response = null;

        // Parse query - extract "query" element
        if (query != null) {
            String params[] = query.split("[&]");
            for (String param : params) {
                int i = param.indexOf('=');
                String name = param.substring(0, i);
                String value = URLDecoder.decode(param.substring(i + 1), "UTF-8");

                if ("query".equalsIgnoreCase(name)) {
                    response = getResponse(URLDecoder.decode(param.substring(i + 1), "UTF-8"));
                }
            }
        }

        t.sendResponseHeaders(200, response == null ? 0 : response.length());
        OutputStream os = t.getResponseBody();
        os.write(response == null ? "".getBytes() : response.getBytes());
        os.close();

    }

    public MockQueryHit getHit(String query, Integer rank) {
        if (!hitMap.containsKey(query)) {
            return null;
        }
        if (rank >= hitMap.get(query).size()) {
            return null;
        }
        return hitMap.get(query).get(rank);
    }

    public MockQueryHit newHit() {
        return new MockQueryHit(this);
    }

    public void addHit(String query, MockQueryHit hit) {
        if (!hitMap.containsKey(query)) {
            hitMap.put(query, new ArrayList<>());
        }
        hitMap.get(query).add(hit);
    }

    private String getResponse(String query) throws IOException {
        List<MockQueryHit> hits = hitMap.get(query);
        if (hits == null) {
            return null;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator g = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);

        writeResultStart(g, hits.size());
        for (MockQueryHit hit : hits) {
            writeHit(g, hit);
        }
        writeResultsEnd(g);
        g.close();

        return out.toString();
    }

    private void writeHit(JsonGenerator g, MockQueryHit hit) throws IOException {
        g.writeStartObject();

        g.writeFieldName("id");
        g.writeString(hit.id);

        g.writeFieldName("relevance");
        g.writeNumber(hit.relevance);

        g.writeFieldName("fields");
        g.writeStartObject();

        g.writeFieldName("sddocname");
        g.writeString(hit.fieldSddocname);

        g.writeFieldName("date");
        g.writeString(hit.fieldDate);

        g.writeFieldName("content");
        g.writeString(hit.fieldContent);

        g.writeFieldName("id");
        g.writeString(hit.fieldId);

        g.writeEndObject();
        g.writeEndObject();
    }

    private void writeResultStart(JsonGenerator g, int count) throws IOException {
        g.writeStartObject();
        g.writeFieldName("root");

        g.writeStartObject();

        g.writeFieldName("id");
        g.writeString("toplevel");

        g.writeFieldName("relevance");
        g.writeNumber(1);

        g.writeFieldName("fields");
        g.writeStartObject();
        g.writeFieldName("totalCount");
        g.writeNumber(count);
        g.writeEndObject();

        g.writeFieldName("coverage");
        g.writeStartObject();
        g.writeFieldName("coverage");
        g.writeNumber(100);
        // ... more stuff here usually
        g.writeEndObject();

        g.writeFieldName("children");
        g.writeStartArray();

        if (!childNode.isEmpty()) {
            g.writeStartObject();
            g.writeFieldName(childNode);
            g.writeStartArray();
        }
    }

    private void writeResultsEnd(JsonGenerator g) throws IOException {
        if (!childNode.isEmpty()) {
            g.writeEndArray();
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
        g.writeEndObject();
    }

    public static class MockQueryHit {

        private final MockQueryHandler handler;

        public String id;
        public Double relevance;
        public String fieldSddocname;
        public String fieldDate;
        public String fieldContent;
        public String fieldId;

        private MockQueryHit(MockQueryHandler handler) {
            this.handler = handler;
        }

        public void add(String query) {
            handler.addHit(query, this);
        }

        public MockQueryHit setId(String id) {
            this.id = id;
            return this;
        }

        public MockQueryHit setRelevance(Double relevance) {
            this.relevance = relevance;
            return this;
        }

        public MockQueryHit setFieldSddocname(String fieldSddocname) {
            this.fieldSddocname = fieldSddocname;
            return this;
        }

        public MockQueryHit setFieldDate(String fieldDate) {
            this.fieldDate = fieldDate;
            return this;
        }

        public MockQueryHit setFieldContent(String fieldContent) {
            this.fieldContent = fieldContent;
            return this;
        }

        public MockQueryHit setFieldId(String fieldId) {
            this.fieldId = fieldId;
            return this;
        }
    }

}
