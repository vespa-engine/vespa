// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.document.DocumentId;
import com.yahoo.messagebus.Message;
import com.yahoo.text.Utf8String;

import java.util.Arrays;

public class FeedErrorMessage extends Message {

    private long sequenceId;

    public FeedErrorMessage(String operationId) {
        try {
            DocumentId id = new DocumentId(operationId);
            sequenceId = Arrays.hashCode(id.getGlobalId());
        } catch (Exception e) {
            sequenceId = 0;
        }
    }

    @Override
    public Utf8String getProtocol() {
        return new Utf8String("vespa-feed-handler-internal-bogus-protocol");
    }

    @Override
    public int getType() {
        return 1234;
    }

    @Override
    public boolean hasSequenceId() {
        return true;
    }

    @Override
    public long getSequenceId() {
        return sequenceId;
    }

}
