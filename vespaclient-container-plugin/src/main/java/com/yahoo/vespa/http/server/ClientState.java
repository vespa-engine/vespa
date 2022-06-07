// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.messagebus.shared.SharedSourceSession;

import java.util.concurrent.BlockingQueue;

/**
 * The state of a client session, used to save replies when client disconnects.
 *
 * @author Steinar Knutsen
 */
public class ClientState {

    public final int pending;
    public final long creationTime;
    public final BlockingQueue<OperationStatus> feedReplies;
    public final ReferencedResource<SharedSourceSession> sourceSession;
    public final Metric.Context metricContext;

    public final long prevOpsPerSecTime; // previous measurement time of OPS
    public final double operationsForOpsPerSec;

    public ClientState(int pending, BlockingQueue<OperationStatus> feedReplies,
                ReferencedResource<SharedSourceSession> sourceSession, Metric.Context metricContext,
                long prevOpsPerSecTime, double operationsForOpsPerSec) {
        super();
        this.pending = pending;
        this.feedReplies = feedReplies;
        this.sourceSession = sourceSession;
        this.metricContext = metricContext;
        creationTime = System.currentTimeMillis();
        this.prevOpsPerSecTime = prevOpsPerSecTime;
        this.operationsForOpsPerSec = operationsForOpsPerSec;
    }

}
