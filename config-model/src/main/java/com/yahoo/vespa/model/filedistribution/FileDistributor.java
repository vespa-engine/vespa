// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.vespa.model.Host;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps track of what files to send with file distribution
 *
 * @author Tony Vaagenes
 */
public class FileDistributor {

    /** A map from file reference to the hosts to which that file reference should be distributed */
    private final Set<FileReference> fileReferences = new LinkedHashSet<>();

    public FileDistributor() { }

    public void sendFileReference(FileReference reference) {
        fileReferences.add(reference);
    }

    public Set<FileReference> allFilesToSend() {
        return Set.copyOf(fileReferences);
    }

}
