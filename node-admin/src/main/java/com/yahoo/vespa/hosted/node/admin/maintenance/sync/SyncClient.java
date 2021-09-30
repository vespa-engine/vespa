// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.util.List;

/**
 * @author freva
 */
public interface SyncClient {

    /**
     * Syncs the given files, will only upload each file once.
     *
     * @param context context used to log which files were synced
     * @param syncFileInfos list of files and their metadata to sync
     * @param limit max number of files to upload for this invocation, to avoid blocking for too long
     * @return true iff any files were uploaded
     */
    boolean sync(TaskContext context, List<SyncFileInfo> syncFileInfos, int limit);
}
