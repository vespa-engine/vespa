// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Keeps track of what files to send with file distribution
 *
 * @author Tony Vaagenes
 * @author hmusum
 */
public class FileReferencesRepository {

    /** A set of file references that should be distributed */
    private final Set<FileReference> fileReferences = new LinkedHashSet<>();

    public FileReferencesRepository() { }

    public void add(FileReference reference) { fileReferences.add(reference); }

    public Set<FileReference> allFileReferences() { return Set.copyOf(fileReferences); }

}
