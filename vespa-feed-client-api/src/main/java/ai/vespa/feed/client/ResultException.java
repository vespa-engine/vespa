// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedException;
import ai.vespa.feed.client.OperationParameters;

import java.util.Optional;

/**
 * Signals that the document API in the feed container returned a failure result for a feed operation.
 *
 * @author jonmv
 */
public class ResultException extends FeedException {

    private final String trace;

    public ResultException(DocumentId documentId, String message, String trace) {
        super(documentId, message);
        this.trace = trace;
    }

    /** Holds the trace, if the failed operation had a {@link OperationParameters#tracelevel(int)} higher than 0. */
    public Optional<String> getTrace() {
        return Optional.ofNullable(trace);
    }

}
