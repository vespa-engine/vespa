// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.filedistribution;

import com.google.common.util.concurrent.SettableFuture;
import com.yahoo.config.FileReference;

import java.io.File;
import java.util.Optional;

public class FileReferenceDownload {

    private final FileReference fileReference;
    private final SettableFuture<Optional<File>> future;
    // If a config server wants to download from another config server (because it does not have the
    // file itself) we set this flag to true to avoid an eternal loop
    private final boolean downloadFromOtherSourceIfNotFound;

    public FileReferenceDownload(FileReference fileReference) {
        this(fileReference, true);
    }

    public FileReferenceDownload(FileReference fileReference, boolean downloadFromOtherSourceIfNotFound) {
        this.fileReference = fileReference;
        this.future = SettableFuture.create();
        this.downloadFromOtherSourceIfNotFound = downloadFromOtherSourceIfNotFound;
    }

    FileReference fileReference() {
        return fileReference;
    }

    SettableFuture<Optional<File>> future() {
        return future;
    }

    boolean downloadFromOtherSourceIfNotFound() {
        return downloadFromOtherSourceIfNotFound;
    }
}
