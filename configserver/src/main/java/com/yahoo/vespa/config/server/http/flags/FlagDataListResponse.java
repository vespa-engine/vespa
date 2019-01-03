// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.flags;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
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
    private final boolean recursive;

    public FlagDataListResponse(String flagsV1Uri, Map<FlagId, FlagData> flags, boolean recursive) {
        super(Response.Status.OK);
        this.flagsV1Uri = flagsV1Uri;
        this.flags = flags;
        this.recursive = recursive;
    }

    @Override
    public void render(OutputStream outputStream) {
        ObjectNode rootNode = mapper.createObjectNode();
        ArrayNode flagsArray = rootNode.putArray("flags");
        // Order flags by ID
        new TreeMap<>(this.flags).forEach((flagId, flagData) -> {
            if (recursive) {
                flagsArray.add(flagData.toJsonNode());
            } else {
                ObjectNode object = flagsArray.addObject();
                object.put("id", flagId.toString());
                object.put("url", flagsV1Uri + "/data/" + flagId.toString());
            }
        });
        uncheck(() -> mapper.writeValue(outputStream, rootNode));
    }

    @Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }
}
