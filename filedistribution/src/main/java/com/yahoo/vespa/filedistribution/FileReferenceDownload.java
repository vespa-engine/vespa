// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;

import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class FileReferenceDownload {

    private final FileReference fileReference;
    private final CompletableFuture<Optional<File>> future;
    // If a config server wants to download from another config server (because it does not have the
    // file itself) we set this flag to false to avoid an eternal loop
    private final boolean downloadFromOtherSourceIfNotFound;
    private final String client;

    public FileReferenceDownload(FileReference fileReference, String client) {
        this(fileReference, client, true);
    }

    public FileReferenceDownload(FileReference fileReference, String client, boolean downloadFromOtherSourceIfNotFound) {
        Objects.requireNonNull(fileReference, "file reference cannot be null");
        this.fileReference = fileReference;
        this.future = new CompletableFuture<>();
        this.downloadFromOtherSourceIfNotFound = downloadFromOtherSourceIfNotFound;
        this.client = client;
    }

    public FileReference fileReference() {
        return fileReference;
    }

    CompletableFuture<Optional<File>> future() {
        return future;
    }

    public boolean downloadFromOtherSourceIfNotFound() {
        return downloadFromOtherSourceIfNotFound;
    }

    public String client() { return client;  }

    @Override
    public String toString() {
        return fileReference + ", client: " + client;
    }

}
