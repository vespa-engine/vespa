// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.Optional;

/**
 * Signals that an error occurred during feeding
 *
 * @author bjorncs
 */
public class FeedException extends RuntimeException {

    private final DocumentId documentId;

    public FeedException(String message) {
        super(message);
        this.documentId = null;
    }

    public FeedException(DocumentId documentId, String message) {
        super(message);
        this.documentId = documentId;
    }

    public FeedException(String message, Throwable cause) {
        super(message, cause);
        this.documentId = null;
    }

    public FeedException(Throwable cause) {
        super(cause);
        this.documentId = null;
    }

    public FeedException(DocumentId documentId, Throwable cause) {
        super(cause);
        this.documentId = documentId;
    }

    public FeedException(DocumentId documentId, String message, Throwable cause) {
        super(message, cause);
        this.documentId = documentId;
    }

    public Optional<DocumentId> documentId() { return Optional.ofNullable(documentId); }

}
