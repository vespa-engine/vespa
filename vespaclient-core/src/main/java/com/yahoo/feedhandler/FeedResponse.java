// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.feedapi.SharedSender;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class FeedResponse implements SharedSender.ResultCallback {

    private final static Logger log = Logger.getLogger(FeedResponse.class.getName());
    private final List<String> errors = new ArrayList<>();
    private final StringBuilder traces = new StringBuilder();
    private final RouteMetricSet metrics;
    private boolean abortOnError = false;
    private boolean isAborted = false;
    private final SharedSender.Pending pendingNumber = new SharedSender.Pending();

    FeedResponse(RouteMetricSet metrics) {
        this.metrics = metrics;
    }

    public boolean isAborted() {
        return isAborted;
    }

    void setAbortOnFeedError(boolean abort) {
        abortOnError = abort;
    }

    private String prettyPrint(Message m) {
        if (m instanceof PutDocumentMessage) {
            return "PUT[" + ((PutDocumentMessage)m).getDocumentPut().getDocument().getId() + "] ";
        }
        if (m instanceof RemoveDocumentMessage) {
            return "REMOVE[" + ((RemoveDocumentMessage)m).getDocumentId() + "] ";
        }
        if (m instanceof UpdateDocumentMessage) {
            return "UPDATE[" + ((UpdateDocumentMessage)m).getDocumentUpdate().getId() + "] ";
        }

        return "";
    }

    public synchronized boolean handleReply(Reply reply) {
        metrics.addReply(reply);
        if (reply.getTrace().getLevel() > 0) {
            String str = reply.getTrace().toString();
            traces.append(str);
            System.out.println(str);
        }

        if (containsFatalErrors(reply.getErrors())) {
            for (int i = 0; i < reply.getNumErrors(); ++i) {
                Error err = reply.getError(i);
                StringBuilder out = new StringBuilder(prettyPrint(reply.getMessage()));
                out.append("[").append(DocumentProtocol.getErrorName(err.getCode())).append("] ");
                if (err.getService() != null) {
                    out.append("(").append(err.getService()).append(") ");
                }
                out.append(err.getMessage());

                String str = out.toString();
                log.finest(str);
                addError(err);
            }
            if (abortOnError) {
                isAborted = true;
                return false;
            }
        }
        return true;
    }

    public SharedSender.Pending getPending() { return pendingNumber; }

    public void done() {
        metrics.done();
    }

    FeedResponse addXMLParseError(String error) {
        errors.add(error);
        return this;
    }

    public FeedResponse addError(Error error) {
        errors.add(error.toString());
        return this;
    }

    public List<String> getErrorList() {
        return errors;
    }

    private static boolean containsFatalErrors(Stream<Error> errors) {
        return errors.anyMatch(e -> e.getCode() != DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED);
    }

    public boolean isSuccess() {
        return errors.isEmpty();
    }

}
