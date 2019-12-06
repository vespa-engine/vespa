// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import java.nio.file.Path;

/**
 * Utility for manipulating the initial file system the Docker container will start with.
 *
 * @author hakon
 */
public interface ContainerData {
    /**
     * Add or overwrite file in container at path.
     *
     * @param pathInContainer The path to the file inside the container, absolute or relative root /.
     * @param data            The content of the file.
     */
    void addFile(Path pathInContainer, String data);

    /**
     * Add or append file in container at path
     * @param pathInContainer The path to the file inside the container, absolute or relative root /.
     * @param data The content to append.
     */
    void appendFile(Path pathInContainer, String data);

    /**
     * Add directory in container at path.
     *
     * @param pathInContainer The path to the directory inside the container, absolute or relative root /.
     */
    void addDirectory(Path pathInContainer);

    /**
     * Symlink to a file in container at path.
     *
     * @param symlink The path to the symlink inside the container, absolute or relative root /.
     * @param target The path to the target file for the symbolic link inside the container, absolute or relative root /.
     */
    void createSymlink(Path symlink, Path target);
}

