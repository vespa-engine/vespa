// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags.http;

import com.yahoo.json.Jackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.flags.json.wire.WireFlagDataList;

import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakonhall
 */
public class FlagDataListResponse extends HttpResponse {
    private static ObjectMapper mapper = Jackson.mapper();

    private final String flagsV1Uri;
    private final TreeMap<FlagId, FlagData> flags;
    private final boolean recursive;

    public FlagDataListResponse(String flagsV1Uri, Map<FlagId, FlagData> flags, boolean recursive) {
        super(Response.Status.OK);
        this.flagsV1Uri = flagsV1Uri;
        this.flags = new TreeMap<>(flags);
        this.recursive = recursive;
    }

    @Override
    public void render(OutputStream outputStream) {
        if (recursive) {
            WireFlagDataList list = new WireFlagDataList();
            flags.values().forEach(flagData -> list.flags.add(flagData.toWire()));
            list.serializeToOutputStream(outputStream);
        } else {
            ObjectNode rootNode = mapper.createObjectNode();
            ArrayNode flagsArray = rootNode.putArray("flags");
            flags.forEach((flagId, flagData) -> {
                ObjectNode object = flagsArray.addObject();
                object.put("id", flagId.toString());
                object.put("url", flagsV1Uri + "/data/" + flagId.toString());
            });
            uncheck(() -> mapper.writeValue(outputStream, rootNode));
        }
    }

    @Override
    public String getContentType() {
        return "application/json";
    }
}
