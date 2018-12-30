// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.flags;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;

import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakonhall
 */
public class FlagDataListResponse extends HttpResponse {
    private static ObjectMapper mapper = new ObjectMapper();

    private final String flagsV1Uri;
    private final Map<FlagId, FlagData> flags;
    private final boolean showDataInsteadOfUrl;

    public FlagDataListResponse(String flagsV1Uri, Map<FlagId, FlagData> flags, boolean showDataInsteadOfUrl) {
        super(Response.Status.OK);
        this.flagsV1Uri = flagsV1Uri;
        this.flags = flags;
        this.showDataInsteadOfUrl = showDataInsteadOfUrl;
    }

    @Override
    public void render(OutputStream outputStream) {
        ObjectNode rootNode = mapper.createObjectNode();
        // Order flags by ID
        new TreeMap<>(flags).forEach((flagId, flagData) -> {
            if (showDataInsteadOfUrl) {
                rootNode.set(flagId.toString(), flagData.toJsonNode());
            } else {
                rootNode.putObject(flagId.toString()).put("url", flagsV1Uri + "/data/" + flagId.toString());
            }
        });
        uncheck(() -> mapper.writeValue(outputStream, rootNode));
    }

    @Override
    public String getContentType() {
        return "application/json";
    }
}
