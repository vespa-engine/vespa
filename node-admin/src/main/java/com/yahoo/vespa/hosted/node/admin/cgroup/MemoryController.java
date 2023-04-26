// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

/**
 * Represents a cgroup v2 memory controller, i.e. all files named memory.*
 *
 * @author hakonhall
 */
public class MemoryController {
    private final ControlGroup cgroup;

    MemoryController(ControlGroup cgroup) {
        this.cgroup = cgroup;
    }

    /** @return Maximum amount of memory that can be used by the cgroup and its descendants. */
    public Size readMax() {
        return Size.from(cgroup.unixPath().resolve("memory.max").readUtf8File().strip());
    }

    /** @return The total amount of memory currently being used by the cgroup and its descendants, in bytes. */
    public Size readCurrent() {
        return Size.from(cgroup.unixPath().resolve("memory.current").readUtf8File().strip());
    }

    /** @return Number of bytes used to cache filesystem data, including tmpfs and shared memory. */
    public Size readFileSystemCache() {
        return Size.from(readField(cgroup.unixPath().resolve("memory.stat"), "file"));
    }

    private static String readField(UnixPath path, String fieldName) {
        return path.readAllLines()
                   .stream()
                   .map(line -> line.split("\\s+"))
                   .filter(fields -> fields.length == 2)
                   .filter(fields -> fieldName.equals(fields[0]))
                   .map(fields -> fields[1])
                   .findFirst()
                   .orElseThrow(() -> new IllegalArgumentException("No such field: " + fieldName));
    }
}
