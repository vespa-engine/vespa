// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;

import java.util.List;

public class CombinedLegacyRegistry implements FileRegistry {
    private final FileDBRegistry legacy;
    private final FileDBRegistry future;

    CombinedLegacyRegistry(FileDBRegistry legacy, FileDBRegistry future) {
        this.legacy = legacy;
        this.future = future;
    }
    @Override
    public FileReference addFile(String relativePath) {
        FileReference reference = legacy.addFile(relativePath);
        return future.addFile(relativePath, reference);
    }

    @Override
    public FileReference addUri(String uri) {
        FileReference reference = legacy.addUri(uri);
        return future.addUri(uri, reference);
    }

    @Override
    public String fileSourceHost() {
        return future.fileSourceHost();
    }

    @Override
    public List<Entry> export() {
        return future.export();
    }
}
