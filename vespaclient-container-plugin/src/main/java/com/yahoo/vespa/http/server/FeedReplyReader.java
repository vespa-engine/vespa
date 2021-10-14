// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import java.util.Set;
import java.util.logging.Logger;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.documentapi.metrics.DocumentOperationStatus;
import com.yahoo.documentapi.metrics.DocumentOperationType;
import com.yahoo.jdisc.Metric;
import java.util.logging.Level;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Trace;
import com.yahoo.vespa.http.client.core.ErrorCode;
import com.yahoo.vespa.http.client.core.OperationStatus;

/**
 * Catch message bus replies and make the available to a given session.
 *
 * @author Steinar Knutsen
 */
public class FeedReplyReader implements ReplyHandler {

    private static final Logger log = Logger.getLogger(FeedReplyReader.class.getName());
    private final Metric metric;
    private final DocumentApiMetrics metricsHelper;

    public FeedReplyReader(Metric metric, DocumentApiMetrics metricsHelper) {
        this.metric = metric;
        this.metricsHelper = metricsHelper;
    }

    @Override
    public void handleReply(Reply reply) {
        Object o = reply.getContext();
        if (!(o instanceof ReplyContext)) {
            return;
        }
        ReplyContext context = (ReplyContext) o;
        final double latencyInSeconds = (System.currentTimeMillis() - context.creationTime) / 1000.0d;
        metric.set(MetricNames.LATENCY, latencyInSeconds, null);

        if (reply.hasErrors()) {
            Set<Integer> errorCodes = reply.getErrorCodes();
            metricsHelper.reportFailure(DocumentOperationType.fromMessage(reply.getMessage()),
                    DocumentOperationStatus.fromMessageBusErrorCodes(errorCodes));
            metric.add(MetricNames.FAILED, 1, null);
            enqueue(context, reply.getError(0).getMessage(), ErrorCode.ERROR,
                    reply.getError(0).getCode() == DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED, reply.getTrace());
        } else {
            metricsHelper.reportSuccessful(DocumentOperationType.fromMessage(reply.getMessage()), latencyInSeconds);
            metric.add(MetricNames.SUCCEEDED, 1, null);
            enqueue(context, "Document processed.", ErrorCode.OK, false, reply.getTrace());
        }
    }

    private void enqueue(ReplyContext context, String message, ErrorCode status, boolean isConditionNotMet, Trace trace) {
        try {
            String traceMessage = (trace != null && trace.getLevel() > 0) ? trace.toString() : "";

            context.feedReplies.put(new OperationStatus(message, context.docId, status, isConditionNotMet, traceMessage));
        } catch (InterruptedException e) {
            log.log(Level.WARNING, 
                    "Interrupted while enqueueing result from putting document with id: " + context.docId);
            Thread.currentThread().interrupt();
        }
    }
}
