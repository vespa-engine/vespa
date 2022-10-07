// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import java.util.Map;
import java.util.TreeMap;

public class MapVisitorMessage extends VisitorMessage {

    private final Map<String, String> data = new TreeMap<String, String>();

    MapVisitorMessage() {
        // must be deserialized into
    }

    public MapVisitorMessage(MapVisitorMessage cmd) {
        data.putAll(cmd.data);
    }

    public Map<String, String> getData() {
        return data;
    }

    @Override
    public DocumentReply createReply() {
        return new VisitorReply(DocumentProtocol.REPLY_MAPVISITOR);
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_MAPVISITOR;
    }

    @Override
    public int getApproxSize() {
        int length = super.getApproxSize() + 4;
        for (Map.Entry<String, String> pairs : data.entrySet()) {
            length += 8;
            length += (pairs.getKey()).length() + pairs.getValue().length();
        }
        return length;
    }

    @Override
    public String toString() {
        return "MapVisitorMessage(" + data.toString() + ")";
    }

}
