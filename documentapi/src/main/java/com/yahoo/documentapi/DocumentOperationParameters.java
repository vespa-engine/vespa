// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.fieldset.FieldSet;
import com.yahoo.document.fieldset.FieldSetRepo;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;

import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;

/** 
 * Optional parameters for a document operation. Immutable class.
 *
 * @author jonmv 
 */
public class DocumentOperationParameters {

    private static final DocumentOperationParameters empty = new DocumentOperationParameters(null, null, null, -1);

    private final DocumentProtocol.Priority priority;
    private final String fieldSet;
    private final String route;
    private final int traceLevel;

    private DocumentOperationParameters(DocumentProtocol.Priority priority, String fieldSet, String route, int traceLevel) {
        this.priority = priority;
        this.fieldSet = fieldSet;
        this.route = route;
        this.traceLevel = traceLevel;
    }

    public static DocumentOperationParameters parameters() {
        return empty;
    }

    /** Sets the priority with which to perform an operation. */
    public DocumentOperationParameters withPriority(DocumentProtocol.Priority priority) {
        return new DocumentOperationParameters(requireNonNull(priority), fieldSet, route, traceLevel);
    }

    /** Sets the field set used for retrieval. */
    public DocumentOperationParameters withFieldSet(FieldSet fieldSet) {
        return new DocumentOperationParameters(priority, new FieldSetRepo().serialize(fieldSet), route, traceLevel);
    }

    /** Sets the field set used for retrieval. */
    public DocumentOperationParameters withFieldSet(String fieldSet) {
        return new DocumentOperationParameters(priority, requireNonNull(fieldSet), route, traceLevel);
    }

    /** Sets the route along which to send the operation. */
    public DocumentOperationParameters withRoute(String route) {
        return new DocumentOperationParameters(priority, fieldSet, requireNonNull(route), traceLevel);
    }

    /** Sets the trace level for an operation. */
    public DocumentOperationParameters withTraceLevel(int traceLevel) {
        if (traceLevel < 0 || traceLevel > 9)
            throw new IllegalArgumentException("Trace level must be from 0 (no tracing) to 9 (maximum)");

        return new DocumentOperationParameters(priority, fieldSet, route, traceLevel);
    }

    public Optional<DocumentProtocol.Priority> priority() { return Optional.ofNullable(priority); }
    public Optional<String> fieldSet() { return Optional.ofNullable(fieldSet); }
    public Optional<String> route() { return Optional.ofNullable(route); }
    public OptionalInt traceLevel() { return traceLevel >= 0 ? OptionalInt.of(traceLevel) : OptionalInt.empty(); }

}
