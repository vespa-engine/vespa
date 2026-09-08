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
     * Whether the target document existed when an update was applied, as reported by the {@code wasFound}
     * field of the document/v1 response. An update against a document which does not exist, and which does
     * not specify that the document should be created if missing, is a no-op — such a result has
     * {@code Optional.of(false)} here, while still being a {@link Type#success}.
     *
     * Empty when the server did not report this, i.e. for all non-update operations, and for updates against
     * Vespa versions or configurations which do not include the field.
     */
    default Optional<Boolean> wasFound() { return Optional.empty(); }

}
