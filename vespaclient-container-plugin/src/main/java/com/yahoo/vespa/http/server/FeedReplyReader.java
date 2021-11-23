// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.documentapi.metrics.DocumentOperationStatus;
import com.yahoo.documentapi.metrics.DocumentOperationType;
import com.yahoo.jdisc.Metric;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Trace;
import com.yahoo.vespa.http.client.core.ErrorCode;
import com.yahoo.vespa.http.client.core.OperationStatus;

import java.util.logging.Level;
import java.util.logging.Logger;

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

        DocumentOperationType type = DocumentOperationType.fromMessage(reply.getMessage());
        boolean conditionNotMet = conditionNotMet(reply);
        if (!conditionNotMet && reply.hasErrors()) {
            DocumentOperationStatus status = DocumentOperationStatus.fromMessageBusErrorCodes(reply.getErrorCodes());
            metricsHelper.reportFailure(type, status);
            metric.add(MetricNames.FAILED, 1, null);
            enqueue(context, reply.getError(0).getMessage(), ErrorCode.ERROR, conditionNotMet, reply.getTrace());
        } else {
            metricsHelper.reportSuccessful(type, latencyInSeconds);
            metric.add(MetricNames.SUCCEEDED, 1, null);
            if (conditionNotMet)
                metric.add(MetricNames.TEST_AND_SET_CONDITION_NOT_MET, 1, null);
            enqueue(context, "Document processed.", ErrorCode.OK, false, reply.getTrace());
        }
    }

    private static boolean conditionNotMet(Reply reply) {
        return reply.hasErrors() && reply.getError(0).getCode() == DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED;
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
