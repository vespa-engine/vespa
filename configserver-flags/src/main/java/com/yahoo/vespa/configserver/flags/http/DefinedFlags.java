// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags.http;

import com.yahoo.json.Jackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.flags.FlagDefinition;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;

/**
 * @author hakonhall
 */
public class DefinedFlags extends HttpResponse {
    private static final ObjectMapper mapper = Jackson.mapper();
    private static final Comparator<FlagDefinition> sortByFlagId = Comparator.comparing(flagDefinition -> flagDefinition.getUnboundFlag().id());

    private final List<FlagDefinition> flags;

    public DefinedFlags(List<FlagDefinition> flags) {
        super(Response.Status.OK);
        this.flags = flags;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        ObjectNode rootNode = mapper.createObjectNode();
        flags.stream().sorted(sortByFlagId).forEach(flagDefinition -> {
            ObjectNode definitionNode = rootNode.putObject(flagDefinition.getUnboundFlag().id().toString());
            DefinedFlag.renderFlagDefinition(flagDefinition, definitionNode);
        });
        mapper.writeValue(outputStream, rootNode);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }
}
