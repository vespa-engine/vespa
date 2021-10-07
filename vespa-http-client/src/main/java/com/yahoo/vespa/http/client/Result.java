// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.Exceptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The result of a stream operation. A Result refers to a single document,
 * but may contain more than one Result.Detail instances, as these pertains to a
 * single endpoint, and a Result may wrap data for multiple endpoints.
 *
 * @author Einar M R Rosenvinge
 */
public class Result {

    public enum ResultType {
        OPERATION_EXECUTED,
        TRANSITIVE_ERROR,
        CONDITION_NOT_MET,
        FATAL_ERROR
    }

    private final Document document;
    private final boolean success;
    private final List<Detail> details;
    private final String localTrace;

    public Result(Document document, Collection<Detail> values, StringBuilder localTrace) {
        this.document = document;
        this.details = Collections.unmodifiableList(new ArrayList<>(values));
        this.success = details.stream().allMatch(d -> d.getResultType() == ResultType.OPERATION_EXECUTED);
        this.localTrace = localTrace == null ? null : localTrace.toString();
    }

    /** Returns the document id that this result is for */
    public String getDocumentId() {
        return document.getDocumentId();
    }

    /** Returns the id of the operation this is the result of */
    public String getOperationId() { return document.getOperationId(); }

    /** Returns the document data */
    public CharSequence getDocumentDataAsCharSequence() {
        return document.getDataAsString();
    }

    /** Returns the context of the object if any */
    public Object getContext() {
        return document.getContext();
    }

    /**
     * Returns true if the operation(s) was successful. If at least one {@link Detail}
     * in {@link #getDetails()} is unsuccessful, this will return false.
     */
    public boolean isSuccess() {
        return success;
    }

    public List<Detail> getDetails() { return details; }

    /** Returns whether the operation has been set up with local tracing */
    public boolean hasLocalTrace() {
        return localTrace != null;
    }

    /** Information in a Result for a single operation sent to a single endpoint. */
    public static final class Detail {

        private final ResultType resultType;
        private final Endpoint endpoint;
        private final Exception exception;
        private final String traceMessage;

        public Detail(Endpoint endpoint, ResultType resultType, String traceMessage, Exception e) {
            this.endpoint = endpoint;
            this.resultType = resultType;
            this.exception = e;
            this.traceMessage = traceMessage;
        }

        public Detail(Endpoint endpoint) {
            this.endpoint = endpoint;
            this.resultType = ResultType.OPERATION_EXECUTED;
            this.exception = null;
            this.traceMessage = null;
        }

        /**
         * Returns the endpoint from which the result was received,
         * or null if this failed before being assigned an endpoint
         */
        public Endpoint getEndpoint() {
            return endpoint;
        }

        /** Returns whether the operation was successful */
        public boolean isSuccess() {
            return resultType == ResultType.OPERATION_EXECUTED;
        }

        /** Returns the result of the operation */
        public ResultType getResultType() {
            return resultType;
        }

        /** Returns any exception related to this Detail, if unsuccessful. Might be null. */
        public Exception getException() {
            return exception;
        }

        /** Returns any trace message produces, or null if none */
        public String getTraceMessage() {
            return traceMessage;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("Detail ");
            b.append("resultType=").append(resultType);
            if (exception != null)
                b.append(" exception='").append(Exceptions.toMessageString(exception)).append("'");
            if (traceMessage != null && ! traceMessage.isEmpty())
                b.append(" trace='").append(traceMessage).append("'");
            if (endpoint != null)
                b.append(" endpoint=").append(endpoint);
            return b.toString();
        }

    }

    @Override
    public String toString() {
        return "Result for " + document + " " + (localTrace != null ? localTrace : "");
    }

}
