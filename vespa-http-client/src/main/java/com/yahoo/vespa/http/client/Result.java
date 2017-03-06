// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Exceptions;
import net.jcip.annotations.Immutable;

import java.util.List;

/**
 * The result of a stream operation. A Result refers to a single document,
 * but may contain more than one Result.Detail instances, as these pertains to a
 * single endpoint, and a Result may wrap data for multiple endpoints.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.20
 */
// This should be an interface, but in order to be binary compatible during refactoring we made it abstract.
public abstract class Result {
    /**
     * Returns the document ID that this Result is for.
     *
     * @return the document ID that this Result is for.
     */
    abstract public String getDocumentId();

    /**
     * Returns the document data.
     * @return data as bytebuffer.
     */
    abstract public CharSequence getDocumentDataAsCharSequence();

    /**
     * Returns the context of the object if any.
     * @return context.
     */
    abstract public Object getContext();

    /**
     * Returns true if the operation was successful. If at least one {@link Detail}
     * in {@link #getDetails()} is unsuccessful, this will return false.
     *
     * @return true if the operation was successful.
     */
    abstract public boolean isSuccess();

    /**
     * Returns true if an error is transient, false if it is permanent. Irrelevant
     * if {@link #isSuccess()} is true (returns true in those cases).
     *
     * @return true if an error is transient (or there is no error), false otherwise.
     */
    abstract public boolean isTransient();

    /**
     * Returns true if a error was caused by condition-not-met in the document operation.
     * @return true if condition is not met.
     */
    abstract public boolean isConditionNotMet();

    abstract public List<Detail> getDetails();

    /**
     * Checks if operation has been set up with local tracing.
     * @return true if operation has local trace.
     */
    abstract public boolean hasLocalTrace();

    /**
     * Information in a Result for a single operation sent to a single endpoint.
     *
     * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
     * @since 5.1.20
     */
    @Immutable
    public static final class Detail {
        private final Endpoint endpoint;
        private final boolean success;
        private final boolean _transient;
        private final boolean isConditionNotMet;
        private final Exception exception;
        private final String traceMessage;
        private final long timeStampMillis = System.currentTimeMillis();

        public Detail(Endpoint endpoint, boolean success, boolean _transient, boolean isConditionNotMet, String traceMessage, Exception e) {
            this.endpoint = endpoint;
            this.success = success;
            this._transient = _transient;
            this.isConditionNotMet = isConditionNotMet;
            this.exception = e;
            this.traceMessage = traceMessage;
        }

        public Detail(Endpoint endpoint) {
            this.endpoint = endpoint;
            this.success = true;
            this._transient = true;
            this.isConditionNotMet = false;
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
         * Returns true if the operation was successful.
         *
         * @return true if the operation was successful.
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * Returns true if an error is transient, false if it is permanent. Irrelevant
         * if {@link #isSuccess()} is true (returns true in those cases).
         *
         * @return true if an error is transient (or there is no error), false otherwise.
         */
        public boolean isTransient() {
            return _transient;
        }

        /**
         * Returns true if a condition in the document operation was not met.
         * @return if condition not met in operation.
         */
        public boolean isConditionNotMet() {
            return isConditionNotMet;
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
            b.append("success=").append(success).append(" ");
            if (!success) {
                b.append("transient=").append(_transient).append(" ");
                b.append("conditionNotMet=").append(isConditionNotMet).append(" ");
            }
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
}
