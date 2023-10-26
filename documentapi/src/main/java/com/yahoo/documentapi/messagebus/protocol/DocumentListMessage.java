// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;

import java.util.ArrayList;
import java.util.List;

public class DocumentListMessage extends VisitorMessage {

    private BucketId bucket = new BucketId(16, 0);
    private final List<DocumentListEntry> entries = new ArrayList<DocumentListEntry>();

    public DocumentListMessage() {
        // empty
    }

    public DocumentListMessage(DocumentListMessage cmd) {
        bucket = cmd.bucket;
        entries.addAll(cmd.entries);
    }

    public BucketId getBucketId() {
        return bucket;
    }

    public void setBucketId(BucketId id) {
        bucket = id;
    }

    public List<DocumentListEntry> getDocuments() {
        return entries;
    }

    @Override
    public DocumentReply createReply() {
        return new VisitorReply(DocumentProtocol.REPLY_DOCUMENTLIST);
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_DOCUMENTLIST;
    }

    @Override
    public int getApproxSize() {
        return DocumentListEntry.getApproxSize() * entries.size();
    }

    @Override
    public String toString() {
        return "DocumentListMessage(" + entries.toString() + ")";
    }
}
