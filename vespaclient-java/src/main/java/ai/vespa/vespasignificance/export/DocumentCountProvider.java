// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.export;

import java.io.IOException;

/**
 * Strategy for finding the document count on current node.
 *
 * @author johsol
 */
@FunctionalInterface
public interface DocumentCountProvider {
    long getDocumentCount() throws IOException;
}
