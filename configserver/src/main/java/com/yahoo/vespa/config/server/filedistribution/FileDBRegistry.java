// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.net.HostName;
import com.yahoo.text.Utf8;
import net.jpountz.xxhash.XXHashFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Tony Vaagenes
 */
public class FileDBRegistry implements FileRegistry {

    private final AddFileInterface manager;
    private List<Entry> entries = new ArrayList<>();
    private final Map<String, FileReference> fileReferenceCache = new HashMap<>();

    public FileDBRegistry(AddFileInterface manager) {
        this.manager = manager;
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
        String relativePath = uriToRelativeFile(uri);
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

    private static String uriToRelativeFile(String uri) {
        String relative = "uri/" + String.valueOf(XXHashFactory.nativeInstance().hash64().hash(ByteBuffer.wrap(Utf8.toBytes(uri)), 0));
        if (uri.endsWith(".json")) {
            relative += ".json";
        } else if (uri.endsWith(".json.lz4")) {
            relative += ".json.lz4";
        } else if (uri.endsWith(".lz4")) {
            relative += ".lz4";
        }
        return relative;
    }

}
