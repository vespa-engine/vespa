// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.documentapi.metrics.DocumentOperationType;
import com.yahoo.vespa.http.client.core.OperationStatus;

import java.util.concurrent.BlockingQueue;

/**
 * Mapping between document ID and client session.
 *
 * @author Steinar Knutsen
 */
public class ReplyContext {

    public final String docId;
    public DocumentOperationType documentOperationType;
    public final BlockingQueue<OperationStatus> feedReplies;
    public final long creationTime;

    public ReplyContext(String docId, BlockingQueue<OperationStatus> feedReplies, DocumentOperationType documentOperationType) {
        this.docId = docId;
        this.feedReplies = feedReplies;
        this.creationTime = System.currentTimeMillis();
    }

}