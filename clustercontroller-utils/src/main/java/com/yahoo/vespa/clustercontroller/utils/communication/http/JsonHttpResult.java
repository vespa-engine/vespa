// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonHttpResult extends HttpResult {

    private static final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json;
    private boolean failedParsing = false;


    public JsonHttpResult() {
        addHeader("Content-Type", "application/json");
    }

    public JsonHttpResult(HttpResult other) {
        super(other);

        if (other.getContent() == null) {
            setParsedJson(new ObjectNode(mapper.getNodeFactory()));
            return;
        }
        try{
            if (other.getContent() instanceof JsonNode jsonContent) {
                setParsedJson(jsonContent);
            } else {
                setParsedJson(mapper.readTree(other.getContent().toString()));
            }
        }
        catch (JsonProcessingException e) {
            failedParsing = true;
            setParsedJson(createErrorJson(e.getMessage(), other));
        }
    }

    private JsonNode createErrorJson(String error, HttpResult other) {
        ObjectNode root = new ObjectNode(mapper.getNodeFactory());
        root.put("error", "Invalid JSON in output: " + error);
        root.put("output", other.getContent().toString());
        return root;
    }

    public JsonHttpResult setJson(JsonNode o) {
        setContent(o);
        json = o;
        return this;
    }

    private void setParsedJson(JsonNode o) {
        json = o;
    }

    public JsonNode getJson() {
        return json;
    }

    @Override
    public void printContent(StringBuilder sb) {
        if (failedParsing || json == null) {
            super.printContent(sb);
        }
        else {
            sb.append("JSON: ");
            sb.append(json.toPrettyString());
        }
    }

}
