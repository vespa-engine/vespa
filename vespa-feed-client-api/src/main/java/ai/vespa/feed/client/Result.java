// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
}
