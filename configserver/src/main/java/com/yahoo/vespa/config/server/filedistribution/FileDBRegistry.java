// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.net.HostName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author tonytv
 */
public class FileDBRegistry implements FileRegistry {

    private final AddFileInterface manager;
    private List<Entry> entries = new ArrayList<>();
    private final Map<String, FileReference> fileReferenceCache = new HashMap<>();

    public FileDBRegistry(AddFileInterface manager) {
        this.manager = manager;
    }

    public synchronized FileReference addFile(String relativePath, FileReference reference) {
        Optional<FileReference> cachedReference = Optional.ofNullable(fileReferenceCache.get(relativePath));
        return cachedReference.orElseGet(() -> {
            FileReference newRef = manager.addFile(relativePath, reference);
            entries.add(new Entry(relativePath, newRef));
            fileReferenceCache.put(relativePath, newRef);
            return newRef;
        });
    }

    public synchronized FileReference addUri(String uri, FileReference reference) {
        String relativePath = FileRegistry.uriToRelativeFile(uri);
        Optional<FileReference> cachedReference = Optional.ofNullable(fileReferenceCache.get(uri));
        return cachedReference.orElseGet(() -> {
            FileReference newRef = manager.addUri(uri, relativePath, reference);
            entries.add(new Entry(uri, newRef));
            fileReferenceCache.put(uri, newRef);
            return newRef;
        });
    }

    @Override
    public synchronized FileReference addFile(String relativePath) {
        Optional<FileReference> cachedReference = Optional.ofNullable(fileReferenceCache.get(relativePath));
        return cachedReference.orElseGet(() -> {
            FileReference newRef = manager.addFile(relativePath);
            entries.add(new Entry(relativePath, newRef));
            fileReferenceCache.put(relativePath, newRef);
            return newRef;
        });
    }

    @Override
    public synchronized FileReference addUri(String uri) {
        String relativePath = FileRegistry.uriToRelativeFile(uri);
        Optional<FileReference> cachedReference = Optional.ofNullable(fileReferenceCache.get(uri));
        return cachedReference.orElseGet(() -> {
            FileReference newRef = manager.addUri(uri, relativePath);
            entries.add(new Entry(uri, newRef));
            fileReferenceCache.put(uri, newRef);
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
