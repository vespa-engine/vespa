// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance.export;

import com.yahoo.vespasignificance.CommandLineOptions;

import java.nio.file.Paths;

/**
 * This class uses vespa-index-inspect to export significance model from
 * flushed indexes to disk.
 *
 * @author johsol
 */
public class Export {

    ExportClientParameters params;
    String indexDir;


    public Export(ExportClientParameters params) {
        this.params = params;
    }

    public void run() {
        resolveIndexDir();
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

            indexDir = params.indexDir();
        }

        System.out.println("Index directory: " + params.indexDir());
    }
}
