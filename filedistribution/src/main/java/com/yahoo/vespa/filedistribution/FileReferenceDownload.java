// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//  Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.filedistribution;

import com.google.common.util.concurrent.SettableFuture;
import com.yahoo.config.FileReference;

import java.io.File;
import java.util.Optional;

class FileReferenceDownload {
    private final FileReference fileReference;
    private final SettableFuture<Optional<File>> future;

    FileReferenceDownload(FileReference fileReference, SettableFuture<Optional<File>> future) {
        this.fileReference = fileReference;
        this.future = future;
    }

    FileReference fileReference() {
        return fileReference;
    }
    SettableFuture<Optional<File>> future() {
        return future;
    }
}
