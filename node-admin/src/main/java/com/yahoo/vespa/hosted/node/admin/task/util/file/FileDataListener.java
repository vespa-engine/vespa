// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

/**
 * Listens for changes to file data.
 *
 * @author hakon
 */
public interface FileDataListener {
    /** The file attributes and content has been (may have been) modified. */
    void contentUpdated(FileData fileData);
}
