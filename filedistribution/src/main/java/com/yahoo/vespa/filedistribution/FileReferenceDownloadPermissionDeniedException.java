// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;

/**
 * Thrown when a peer (e.g. config server) has denied a request to download a file reference,
 * typically because the requesting peer is unknown or not authorized to access that file reference.
 * This is a permanent failure and should not be retried.
 *
 * @author hmusum
 */
public class FileReferenceDownloadPermissionDeniedException extends RuntimeException {

    public FileReferenceDownloadPermissionDeniedException(FileReference fileReference, String reason) {
        super("Download of " + fileReference + " denied: " + reason);
    }

}
