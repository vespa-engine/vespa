// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.Optional;

/**
 * Result for a document operation which completed normally.
 *
 * @author bjorncs
 * @author jonmv
 */
public interface Result {

    enum Type {
        success,
        conditionNotMet
    }

    Type type();
    DocumentId documentId();
    Optional<String> resultMessage();
    Optional<String> traceMessage();

    /**
     * Returns whether this operation was ignored by Vespa, i.e., it had no effect on the document.
     * This is currently only possible for a (partial) update to a document that does not exist, and
     * which does not specify that the document should be created if it is missing.
     */
    boolean ignored();

}
