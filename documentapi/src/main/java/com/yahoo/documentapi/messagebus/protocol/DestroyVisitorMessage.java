// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

public class DestroyVisitorMessage extends DocumentMessage {

    private String instanceId;

    DestroyVisitorMessage() {
        // must be deserialized into
    }

    public DestroyVisitorMessage(String instanceId) {
        this.instanceId = instanceId;
    }

    public DestroyVisitorMessage(DestroyVisitorMessage cmd) {
        instanceId = cmd.instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public DocumentReply createReply() {
        return new DocumentReply(DocumentProtocol.REPLY_DESTROYVISITOR);
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_DESTROYVISITOR;
    }
}
