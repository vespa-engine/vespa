// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentReply;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.documentapi.metrics.DocumentOperationStatus;
import com.yahoo.documentapi.metrics.DocumentOperationType;
import com.yahoo.jdisc.Metric;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Trace;

import java.util.Map;
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
    private final Metric.Context testAndSetMetricCtx;

    public FeedReplyReader(Metric metric, DocumentApiMetrics metricsHelper) {
        this.metric = metric;
        this.metricsHelper = metricsHelper;
        this.testAndSetMetricCtx = metric.createContext(Map.of("operationType", "testAndSet"));
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
        boolean conditionMet = conditionMet(reply);
        if (reply.hasErrors() && conditionMet) {
            DocumentOperationStatus status = DocumentOperationStatus.fromMessageBusErrorCodes(reply.getErrorCodes());
            metricsHelper.reportFailure(type, status);
            metric.add(MetricNames.FAILED, 1, null);
            enqueue(context, reply.getError(0).getMessage(), ErrorCode.ERROR, false, reply.getTrace());
        } else {
            metricsHelper.reportSuccessful(type, latencyInSeconds);
            metric.add(MetricNames.SUCCEEDED, 1, null);
            if ( ! conditionMet)
                metric.add(MetricNames.CONDITION_NOT_MET, 1, testAndSetMetricCtx);
            if ( ! updateNotFound(reply))
                metric.add(MetricNames.NOT_FOUND, 1, null);
            enqueue(context, "Document processed.", ErrorCode.OK, !conditionMet, reply.getTrace());
        }
    }

    private static boolean conditionMet(Reply reply) {
        return ! reply.hasErrors() || reply.getError(0).getCode() != DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED;
    }

    private static boolean updateNotFound(Reply reply) {
        return       reply instanceof UpdateDocumentReply
                && ! ((UpdateDocumentReply) reply).wasFound()
                &&   reply.getMessage() instanceof UpdateDocumentMessage
                &&   ((UpdateDocumentMessage) reply.getMessage()).getDocumentUpdate() != null
                && ! ((UpdateDocumentMessage) reply.getMessage()).getDocumentUpdate().getCreateIfNonExistent();
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
