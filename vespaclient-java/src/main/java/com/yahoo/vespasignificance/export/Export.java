// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance.export;

import com.yahoo.vespasignificance.CommandLineOptions;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class uses vespa-index-inspect to export significance model from
 * flushed indexes to disk.
 *
 * @author johsol
 */
public class Export {

    ExportClientParameters params;
    Path indexDir;


    public Export(ExportClientParameters params) {
        this.params = params;
    }

    public void run() {
        resolveIndexDir();
        callVespaIndexInspect();
    }

    private void resolveIndexDir() {
        if (params.locateIndex()) {
            System.out.println("Locating index directory");
            indexDir = new IndexLocator().locateIndexDir(params);
        } else {
            if (params.indexDir() == null || params.indexDir().isEmpty()) {
                System.out.println("[error] No index directory specified.");
                System.out.println("Use --index-dir to specify index directory or --locate-index to search for index directory.");
                CommandLineOptions.printExportHelp();
                System.exit(1);
            }

            if (!Paths.get(params.indexDir()).toFile().exists()) {
                System.out.println("[error] Index directory `" + params.indexDir() + "` does not exist.");
                System.exit(1);
            }

            indexDir = Paths.get(params.indexDir());
        }
    }

    private void callVespaIndexInspect() {
        var indexInspect = new VespaIndexInspectClient();
        try {
            var df = indexInspect.dumpWords(indexDir, params.fieldName());
            System.out.println("Extracted values:");
            for (var entry : df.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
