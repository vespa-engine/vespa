// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.fieldset.FieldSet;
import com.yahoo.document.fieldset.FieldSetRepo;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;

/** 
 * Optional parameters for a document operation. Immutable class.
 *
 * @author jonmv 
 */
public class DocumentOperationParameters {

    private static final DocumentOperationParameters empty = new DocumentOperationParameters(null, null, null, -1, null, null);

    private final DocumentProtocol.Priority priority;
    private final String fieldSet;
    private final String route;
    private final int traceLevel;
    private final Instant deadline;
    private final ResponseHandler responseHandler;

    private DocumentOperationParameters(DocumentProtocol.Priority priority, String fieldSet, String route,
                                        int traceLevel, Instant deadline, ResponseHandler responseHandler) {
        this.priority = priority;
        this.fieldSet = fieldSet;
        this.route = route;
        this.traceLevel = traceLevel;
        this.deadline = deadline;
        this.responseHandler = responseHandler;
    }

    public static DocumentOperationParameters parameters() {
        return empty;
    }

    /** Sets the field set used for retrieval. */
    public DocumentOperationParameters withFieldSet(FieldSet fieldSet) {
        return new DocumentOperationParameters(priority, new FieldSetRepo().serialize(fieldSet), route, traceLevel, deadline, responseHandler);
    }

    /** Sets the field set used for retrieval. */
    public DocumentOperationParameters withFieldSet(String fieldSet) {
        return new DocumentOperationParameters(priority, requireNonNull(fieldSet), route, traceLevel, deadline, responseHandler);
    }

    /** Sets the route along which to send the operation. */
    public DocumentOperationParameters withRoute(String route) {
        return new DocumentOperationParameters(priority, fieldSet, requireNonNull(route), traceLevel, deadline, responseHandler);
    }

    /** Sets the trace level for an operation. */
    public DocumentOperationParameters withTraceLevel(int traceLevel) {
        if (traceLevel < 0 || traceLevel > 9)
            throw new IllegalArgumentException("Trace level must be from 0 (no tracing) to 9 (maximum)");

        return new DocumentOperationParameters(priority, fieldSet, route, traceLevel, deadline, responseHandler);
    }

    /** Sets the deadline for an operation. */
    public DocumentOperationParameters withDeadline(Instant deadline) {
        return new DocumentOperationParameters(priority, fieldSet, route, traceLevel, requireNonNull(deadline), responseHandler);
    }

    /** Sets the {@link ResponseHandler} to handle the {@link Response} of an async operation, instead of the session default. */
    public DocumentOperationParameters withResponseHandler(ResponseHandler responseHandler) {
        return new DocumentOperationParameters(priority, fieldSet, route, traceLevel, deadline, requireNonNull(responseHandler));
    }

    public Optional<String> fieldSet() { return Optional.ofNullable(fieldSet); }
    public Optional<String> route() { return Optional.ofNullable(route); }
    public OptionalInt traceLevel() { return traceLevel >= 0 ? OptionalInt.of(traceLevel) : OptionalInt.empty(); }
    public Optional<Instant> deadline() { return Optional.ofNullable(deadline); }
    public Optional<ResponseHandler> responseHandler() { return Optional.ofNullable(responseHandler); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentOperationParameters that = (DocumentOperationParameters) o;
        return traceLevel == that.traceLevel &&
               priority == that.priority &&
               Objects.equals(fieldSet, that.fieldSet) &&
               Objects.equals(route, that.route);
    }

    @Override
    public int hashCode() {
        return Objects.hash(priority, fieldSet, route, traceLevel);
    }

    @Override
    public String toString() {
        return "DocumentOperationParameters{" +
               "priority=" + priority +
               ", fieldSet='" + fieldSet + '\'' +
               ", route='" + route + '\'' +
               ", traceLevel=" + traceLevel +
               '}';
    }

}
