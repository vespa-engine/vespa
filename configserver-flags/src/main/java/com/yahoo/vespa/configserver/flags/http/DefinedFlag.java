// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.flags.FlagDefinition;
import com.yahoo.vespa.flags.json.DimensionHelper;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author hakonhall
 */
public class DefinedFlag extends HttpResponse {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final FlagDefinition flagDefinition;

    public DefinedFlag(FlagDefinition flagDefinition) {
        super(Response.Status.OK);
        this.flagDefinition = flagDefinition;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        ObjectNode rootNode = mapper.createObjectNode();
        renderFlagDefinition(flagDefinition, rootNode);
        mapper.writeValue(outputStream, rootNode);
    }

    static void renderFlagDefinition(FlagDefinition flagDefinition, ObjectNode definitionNode) {
        definitionNode.put("description", flagDefinition.getDescription());
        definitionNode.put("modification-effect", flagDefinition.getModificationEffect());
        ArrayNode ownersNode = mapper.createArrayNode();
        flagDefinition.getOwners().forEach(ownersNode::add);
        definitionNode.set("owners", ownersNode);
        definitionNode.put("createdAt", flagDefinition.getCreatedAt().toString());
        definitionNode.put("expiresAt", flagDefinition.getExpiresAt().toString());
        ArrayNode dimensionsNode = definitionNode.putArray("dimensions");
        flagDefinition.getDimensions().forEach(dimension -> dimensionsNode.add(DimensionHelper.toWire(dimension)));
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

}
