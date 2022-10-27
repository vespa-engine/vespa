// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 * Formats a {@link JsonNode} to a normalized JSON string, see {@link JsonTestHelper}.
 *
 * @author hakonhall
 */
class JsonNodeFormatter {
    private final JsonBuilder builder;

    /** See {@link JsonTestHelper}. */
    static String toNormalizedJson(JsonNode jsonNode, boolean compact) {
        JsonNodeFormatter formatter = new JsonNodeFormatter(compact);
        formatter.appendValue(jsonNode);
        return formatter.toString();
    }

    private JsonNodeFormatter(boolean compact) {
        builder = compact ? JsonBuilder.forCompactJson() : JsonBuilder.forMultiLineJson(2);
    }

    private void appendValue(JsonNode jsonNode) {
        switch (jsonNode.getNodeType()) {
            case OBJECT -> {
                ObjectNode objectNode = (ObjectNode) jsonNode;
                ArrayList<String> fieldNames = new ArrayList<>();
                objectNode.fieldNames().forEachRemaining(fieldNames::add);
                Collections.sort(fieldNames);
                if (fieldNames.isEmpty()) {
                    builder.append("{}");
                } else {
                    boolean firstIteration = true;
                    for (var fieldName : fieldNames) {
                        if (firstIteration) {
                            builder.appendLineAndIndent("{", +1);
                            firstIteration = false;
                        } else {
                            builder.appendLineAndIndent(",");
                        }

                        builder.appendStringValue(fieldName);
                        builder.appendColon();
                        appendValue(objectNode.get(fieldName));
                    }

                    builder.newLineIndentAndAppend(-1, "}");
                }
            }
            case ARRAY -> {
                Iterator<JsonNode> elements = jsonNode.elements();
                if (elements.hasNext()) {
                    builder.appendLineAndIndent("[", +1);
                    appendValue(elements.next());

                    while (elements.hasNext()) {
                        builder.appendLineAndIndent(",");
                        appendValue(elements.next());
                    }

                    builder.newLineIndentAndAppend(-1, "]");
                } else {
                    builder.append("[]");
                }
            }
            case BOOLEAN, NUMBER, NULL -> builder.append(jsonNode.asText());
            case STRING -> builder.appendStringValue(jsonNode.asText());
            case BINARY, MISSING, POJO -> throw new IllegalStateException(jsonNode.getNodeType().toString());
        }
    }

    @Override
    public String toString() { return builder.toString(); }
}
