// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.vespaxmlparser.FeedOperation;

/**
 * Wrapper class for SimpleFeedAccess to send various XML operations.
 */
public class VespaFeedSender {

    private final SimpleFeedAccess sender;

    public VespaFeedSender(SimpleFeedAccess sender) {
        this.sender = sender;
    }

    public boolean isAborted() {
        return sender.isAborted();
    }

    public void sendOperation(FeedOperation op) {
        switch (op.getType()) {
            case DOCUMENT:
                sender.put(op.getDocumentPut());
                break;
            case REMOVE:
                sender.remove(op.getDocumentRemove());
                break;
            case UPDATE:
                sender.update(op.getDocumentUpdate());
                break;
        }
    }

}
