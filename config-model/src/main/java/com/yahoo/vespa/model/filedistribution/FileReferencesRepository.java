// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps track of what files to send with file distribution
 *
 * @author Tony Vaagenes
 * @author hmusum
 */
public class FileReferencesRepository {

    private final FileRegistry fileRegistry;
    public FileReferencesRepository(FileRegistry fileRegistry) {
        this.fileRegistry = fileRegistry;
    }

    public Set<FileReference> allFileReferences() {
        return fileRegistry.export()
                           .stream()
                           .map(e -> e.reference)
                           .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

}
