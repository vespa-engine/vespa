// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

import java.io.IOException;

/**
 * Sink for merged term–document-frequency rows.
 *
 * @author johsol
 */
@FunctionalInterface
public interface TermDfRowSink {

    /**
     * Accepts a single merged row.
     */
    void write(String term, long df) throws IOException;
}