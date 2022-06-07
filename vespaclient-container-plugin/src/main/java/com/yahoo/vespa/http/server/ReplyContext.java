// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import java.util.concurrent.BlockingQueue;

/**
 * Mapping between document ID and client session.
 *
 * @author Steinar Knutsen
 */
public class ReplyContext {

    public final String docId;
    public final BlockingQueue<OperationStatus> feedReplies;
    public final long creationTime;

    public ReplyContext(String docId, BlockingQueue<OperationStatus> feedReplies) {
        this.docId = docId;
        this.feedReplies = feedReplies;
        this.creationTime = System.currentTimeMillis();
    }

}
