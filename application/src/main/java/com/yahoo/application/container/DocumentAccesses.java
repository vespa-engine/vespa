// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.local.LocalDocumentAccess;
import com.yahoo.schema.derived.Deriver;

import java.io.File;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Utility for working with  a {@link LocalDocumentAccess} for unit testing components which require a {@link DocumentAccess}.
 *
 * @author jonmv
 */
public class DocumentAccesses {

    private DocumentAccesses() { }

    /**
     * Reads the {@code .sd} files in the given directory, and returns a {@link LocalDocumentAccess} with these document types.
     * <br>
     * Example usage:
     * <pre>
     * LocalDocumentAccess access = DocumentAccesses.ofSchemas("src/main/application/schemas");
     * </pre>
     */
    public static LocalDocumentAccess createFromSchemas(String schemaDirectory) {
        File[] schemasFiles = new File(schemaDirectory).listFiles(name -> name.toString().endsWith(".sd"));
        if (schemasFiles == null)
            throw new IllegalArgumentException(schemaDirectory + " is not a directory");
        if (schemasFiles.length == 0)
            throw new IllegalArgumentException("No schema files found under " + schemaDirectory);
        DocumentmanagerConfig config = Deriver.getDocumentManagerConfig(Stream.of(schemasFiles)
                                                                              .map(File::toString)
                                                                              .collect(toList())).build();
        return new LocalDocumentAccess(new DocumentAccessParams().setDocumentmanagerConfig(config));
    }

}
