// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.Result;

import java.util.Optional;

/**
 * Result for a document operation which completed normally.
 *
 * @author bjorncs
 * @author jonmv
 */
public class ResultImpl implements Result {

    private final Type type;
    private final DocumentId documentId;
    private final String resultMessage;
    private final String traceMessage;

    ResultImpl(Type type, DocumentId documentId, String resultMessage, String traceMessage) {
        this.type = type;
        this.documentId = documentId;
        this.resultMessage = resultMessage;
        this.traceMessage = traceMessage;
    }

    @Override public Type type() { return type; }
    @Override public DocumentId documentId() { return documentId; }
    @Override public Optional<String> resultMessage() { return Optional.ofNullable(resultMessage); }
    @Override public Optional<String> traceMessage() { return Optional.ofNullable(traceMessage); }

    @Override
    public String toString() {
        return "Result{" +
               "type=" + type +
               ", documentId=" + documentId +
               ", resultMessage='" + resultMessage + '\'' +
               ", traceMessage='" + traceMessage + '\'' +
               '}';
    }

}
