// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.Exceptions;
import net.jcip.annotations.Immutable;

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
// This should be an interface, but in order to be binary compatible during refactoring we made it abstract.
public class Result {

    public enum ResultType {
        OPERATION_EXECUTED,
        TRANSITIVE_ERROR,
        CONDITION_NOT_MET,
        FATAL_ERROR
    }

    private final Document document;
    private final boolean success;
    private final boolean _transient;
    private final List<Detail> details;
    private final String localTrace;

    public Result(Document document, Collection<Detail> values, StringBuilder localTrace) {
        this.document = document;
        this.details = Collections.unmodifiableList(new ArrayList<>(values));
        boolean totalSuccess = true;
        boolean totalTransient = true;
        for (Detail d : details) {
            if (d.getResultType() != ResultType.OPERATION_EXECUTED) {totalSuccess = false; }
            if (d.getResultType() != ResultType.TRANSITIVE_ERROR) {totalTransient = false; }
        }
        this.success = totalSuccess;
        this._transient = totalTransient;
        this.localTrace = localTrace == null ? null : localTrace.toString();
    }


    /**
     * Returns the document ID that this Result is for.
     *
     * @return the document ID that this Result is for.
     */
    public String getDocumentId() {
        return document.getDocumentId();
    }

    /**
     * Returns the document data.
     * @return data as bytebuffer.
     */
    public CharSequence getDocumentDataAsCharSequence() {
        return document.getDataAsString();
    }

    /**
     * Returns the context of the object if any.
     * @return context.
     */
    public Object getContext() {
        return document.getContext();
    }

    /**
     * Returns true if the operation(s) was successful. If at least one {@link Detail}
     * in {@link #getDetails()} is unsuccessful, this will return false.
     *
     * @return true if the operation was successful.
     */
    public boolean isSuccess() {
        return success;
    }
    /**
     * @deprecated use resultType on items getDetails() to check  operations.
     * Returns true if an error is transient, false if it is permanent. Irrelevant
     * if {@link #isSuccess()} is true (returns true in those cases).
     *
     * @return true if an error is transient (or there is no error), false otherwise.
     */
    @Deprecated
    public boolean isTransient() {
        return _transient;
    }

    public List<Detail> getDetails() { return details; }

    /**
     * Checks if operation has been set up with local tracing.
     * @return true if operation has local trace.
     */
    public boolean hasLocalTrace() {
        return localTrace != null;
    }

    /**
     * Information in a Result for a single operation sent to a single endpoint.
     */
    @Immutable
    public static final class Detail {

        private final ResultType resultType;
        private final Endpoint endpoint;
        private final Exception exception;
        private final String traceMessage;
        private final long timeStampMillis = System.currentTimeMillis();

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
         * Returns the endpoint from which the result was received.
         *
         * @return the endpoint from which the result was received.
         */
        public Endpoint getEndpoint() {
            return endpoint;
        }

        /**
         * Check if operation was successful.
         *
         * @return true if the operation was successful.
         */
        public boolean isSuccess() {
            return resultType == ResultType.OPERATION_EXECUTED;
        }

        /**
         * @deprecated use getResultType.
         * Returns true if an error is transient, false if it is permanent. Irrelevant
         * if {@link #isSuccess()} is true (returns true in those cases).
         *
         * @return true if an error is transient (or there is no error), false otherwise.
         */
        @Deprecated
        public boolean isTransient() {
            return resultType == ResultType.TRANSITIVE_ERROR || resultType == ResultType.OPERATION_EXECUTED;
        }

        /**
         * Returns the result of the operation.
         */
        public ResultType getResultType() {
            return resultType;
        }

        /**
         * Returns any exception related to this Detail, if unsuccessful. Might be null.
         *
         * @return any exception related to this Detail, if unsuccessful. Might be null.
         */
        public Exception getException() {
            return exception;
        }

        /**
         * Returns trace message if any from gateway.
         * @return null or trace message.
         */
        public String getTraceMessage() {
            return traceMessage;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("Detail ");
            b.append("resultType=").append(resultType);
            if (exception != null) {
                b.append("exception='").append(Exceptions.toMessageString(exception)).append("' ");
            }
            if (traceMessage != null && ! traceMessage.isEmpty()) {
                b.append("trace='").append(traceMessage).append("' ");
            }
            b.append("endpoint=").append(endpoint);
            b.append(" resultTimeLocally=").append(timeStampMillis).append("\n");
            return b.toString();
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("Result for '").append(document.getDocumentId());
        if (localTrace != null) {
            b.append(localTrace);
        }
        return b.toString();
    }

}
