// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.exports;

import com.yahoo.vespa.configsource.util.FileInfo;
import com.yahoo.vespa.configsource.util.FileInfoCache;

import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author hakon
 */
public class FileContentSupplier<T> implements ConfigSupplier<T> {
    private static final Logger logger = Logger.getLogger(FileContentSupplier.class.getName());

    private final FileInfoCache fileInfoCache;
    private final Deserializer<T> deserializer;

    public FileContentSupplier(Path path, Deserializer<T> deserializer) {
        this.fileInfoCache = new FileInfoCache(path);
        this.deserializer = deserializer;
    }

    @Override
    public Optional<T> getSnapshot() {
        return fileInfoCache.syncAndGet()
                .filter(FileInfo::exists)
                .map(FileInfo::content)
                .map(deserializer::deserialize);
    }
}
