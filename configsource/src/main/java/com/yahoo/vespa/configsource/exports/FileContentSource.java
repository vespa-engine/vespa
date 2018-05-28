// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.exports;

import java.nio.file.Path;

/**
 * A FileContentSource is a {@link ConfigSource} that provides deserialized file content.
 *
 * @author hakon
 */
public class FileContentSource implements ConfigSource {
    public <T> FileContentSupplier<T> newSupplier(Path path, Deserializer<T> deserializer) {
        return new FileContentSupplier<>(path, deserializer);
    }
}
