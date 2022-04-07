// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;

import java.nio.file.Path;

/**
 * Utility for manipulating the initial file system the Docker container will start with.
 *
 * @author hakon
 */
public interface ContainerData {

    /** Add or overwrite file in container at path. */
    void addFile(ContainerPath path, String data);

    /**
     * @param path Container path to write
     * @param data UTF-8 file content
     * @param permissions file permissions, see {@link UnixPath#setPermissions(String)} for format.
     */
    void addFile(ContainerPath path, String data, String permissions);

    /**
     * @param path Container path to create directory at
     * @param permissions optional file permissions, see {@link UnixPath#setPermissions(String)} for format.
     */
    void addDirectory(ContainerPath path, String... permissions);

    /**
     * Symlink to a file in container at path.
     * @param symlink The path to the symlink inside the container
     * @param target The path to the target file for the symbolic link inside the container
     */
    void addSymlink(ContainerPath symlink, Path target);

    /** Writes all the files, directories and symlinks that were previously added */
    void converge(NodeAgentContext context);
}

