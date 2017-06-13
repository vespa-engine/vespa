// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.net.HostName;
import com.yahoo.vespa.filedistribution.FileDistributionManager;
import com.yahoo.config.model.application.provider.FileReferenceCreator;

import java.util.*;

/**
 * @author tonytv
 */
public class FileDBRegistry implements FileRegistry {

    private final FileDistributionManager manager;
    private List<Entry> entries = new ArrayList<>();
    private final Map<String, FileReference> fileReferenceCache = new HashMap<>();

    public FileDBRegistry(FileDistributionManager manager) {
        this.manager = manager;
    }

    @Override
    public synchronized FileReference addFile(String relativePath) {
        Optional<FileReference> cachedReference = Optional.ofNullable(fileReferenceCache.get(relativePath));
        return cachedReference.orElseGet(() -> {
            FileReference newRef = FileReferenceCreator.create(manager.addFile(relativePath));
            entries.add(new Entry(relativePath, newRef));
            fileReferenceCache.put(relativePath, newRef);
            return newRef;
        });
    }

    @Override
    public String fileSourceHost() {
        return HostName.getLocalhost();
    }

    @Override
    public synchronized List<Entry> export() {
        return entries;
    }

}
