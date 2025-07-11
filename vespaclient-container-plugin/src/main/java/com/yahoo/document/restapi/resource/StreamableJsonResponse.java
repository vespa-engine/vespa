// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.messagebus.Trace;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Shared abstraction that can be used for both buffered and streaming response
 * rendering of Document V1 responses.
 */
interface StreamableJsonResponse extends AutoCloseable {
    void commit(int status, boolean fullyApplied) throws IOException;
    void writeDocumentsArrayStart() throws IOException;
    void writeDocumentsArrayEnd() throws IOException;
    void writeDocumentValue(Document document, CompletionHandler completionHandler) throws IOException;
    void writeDocumentRemoval(DocumentId id, CompletionHandler completionHandler) throws IOException;
    // The implementation MUST NOT assume the Supplier is safe to invoke at any other
    // time than during the synchronous invocation of this method. This is because the
    // underlying supplied resource may require locking for safe access.
    void reportUpdatedContinuation(Supplier<VisitorContinuation> continuationSupplier) throws IOException;
    void writeEpilogueContinuation(VisitorContinuation continuation) throws IOException;
    void writeTrace(Trace trace) throws IOException;
    void writeMessage(String message) throws IOException;
    void writeDocumentCount(long count) throws IOException;
    void close() throws IOException; // Narrowed exception specifier
}
