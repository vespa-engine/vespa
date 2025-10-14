// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.export;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Uses vespa-header-inspect to extract count from selector.dat.
 *
 * @author johsol
 */
public class DocIdLimitDocumentCountProvider implements DocumentCountProvider {
    private final VespaFileHeaderInspectClient fileHeaderInspectClient;
    private final Path indexDir;

    private static final long RESERVED_FOR_LID_SPACE = 1;

    DocIdLimitDocumentCountProvider(VespaFileHeaderInspectClient fileHeaderInspectClient, Path indexDir) {
        this.fileHeaderInspectClient = fileHeaderInspectClient;
        this.indexDir = indexDir;
    }

    @Override
    public long getDocumentCount() throws IOException {
        return fileHeaderInspectClient.readDocIdLimit(indexDir) - RESERVED_FOR_LID_SPACE;
    }
}
