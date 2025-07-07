// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.messagebus.TraceNode;

import java.io.IOException;

/**
 * Simple wrapper for rendering trace trees as recursive JSON structures.
 */
class TraceJsonRenderer {

    private TraceJsonRenderer() {}

    static void writeTrace(JsonGenerator json, TraceNode node) throws IOException {
        if (node.hasNote()) {
            json.writeStringField("message", node.getNote());
        }
        if (!node.isLeaf()) {
            json.writeArrayFieldStart(node.isStrict() ? "trace" : "fork");
            for (int i = 0; i < node.getNumChildren(); i++) {
                json.writeStartObject();
                writeTrace(json, node.getChild(i));
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }

}
