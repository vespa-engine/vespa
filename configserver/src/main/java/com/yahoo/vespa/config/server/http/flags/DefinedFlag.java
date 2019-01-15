// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.flags;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.flags.FlagDefinition;
import com.yahoo.vespa.flags.json.DimensionHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

/**
 * @author hakonhall
 */
public class DefinedFlag extends HttpResponse {
    private static ObjectMapper mapper = new ObjectMapper();

    private final Optional<FlagDefinition> flagDefinition;

    public DefinedFlag(Optional<FlagDefinition> flagDefinition) {
        super(Response.Status.OK);
        this.flagDefinition = flagDefinition;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        ObjectNode rootNode = mapper.createObjectNode();
        flagDefinition.ifPresent(definition -> renderFlagDefinition(definition, rootNode));
        mapper.writeValue(outputStream, rootNode);
    }

    static void renderFlagDefinition(FlagDefinition flagDefinition, ObjectNode definitionNode) {
        definitionNode.put("description", flagDefinition.getDescription());
        definitionNode.put("modification-effect", flagDefinition.getModificationEffect());
        ArrayNode dimensionsNode = definitionNode.putArray("dimensions");
        flagDefinition.getDimensions().forEach(dimension -> dimensionsNode.add(DimensionHelper.toWire(dimension)));
    }

    @Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }
}
