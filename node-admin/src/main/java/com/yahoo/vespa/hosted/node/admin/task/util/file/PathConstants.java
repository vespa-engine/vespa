// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author hakon
 */
public class PathConstants {
    /** /etc root configuration directory for Node Admin. */
    public static final Path ETC_VESPA_PATH = Paths.get("/etc/vespa");

    private PathConstants() {} // Prevent instantiation
}
