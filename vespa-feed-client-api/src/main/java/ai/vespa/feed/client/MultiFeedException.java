// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates multiple instances of {@link FeedException}
 *
 * @author bjorncs
 */
public class MultiFeedException extends RuntimeException {

    private final List<FeedException> exceptions;

    public MultiFeedException(Collection<FeedException> exceptions) {
        super(toMessage(exceptions));
        this.exceptions = Collections.unmodifiableList(new ArrayList<>(exceptions));
    }

    public Collection<FeedException> feedExceptions() { return exceptions; }

    public Set<DocumentId> documentIds() {
        return exceptions.stream()
                .filter(e -> e.documentId().isPresent())
                .map(e -> e.documentId().get())
                .collect(Collectors.toSet());
    }

    private static String toMessage(Collection<FeedException> exceptions) {
        return String.format("%d feed operations failed", exceptions.size());
    }

}
