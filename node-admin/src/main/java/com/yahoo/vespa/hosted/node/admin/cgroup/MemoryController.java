// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import java.util.List;
import java.util.Optional;

/**
 * Represents a cgroup v2 memory controller, i.e. all memory.* files.
 *
 * @author hakonhall
 */
public class MemoryController {
    private final Cgroup cgroup;

    MemoryController(Cgroup cgroup) {
        this.cgroup = cgroup;
    }

    /** @return Maximum amount of memory that can be used by the cgroup and its descendants. */
    public Size readMax() {
        return cgroup.readSize("memory.max");
    }

    /** @return The total amount of memory currently being used by the cgroup and its descendants, in bytes. */
    public Size readCurrent() {
        return cgroup.readSize("memory.current");
    }

    /** @return The total amount of memory currently being used by the cgroup and its descendants, in bytes. */
    public Optional<Size> readCurrentIfExists() {
        return cgroup.readIfExists("memory.current").map(Size::from);
    }

    public Stats readStat() {
        var lines = cgroup.readLines("memory.stat");
        return new Stats(
                Size.from(readField(lines, "file")), Size.from(readField(lines, "sock")), Size.from(readField(lines, "slab")),
                Size.from(readField(lines, "slab_reclaimable")), Size.from(readField(lines, "anon")));
    }

    private static String readField(List<String> lines, String fieldName) {
        return lines.stream()
                    .map(line -> line.split("\\s+"))
                    .filter(fields -> fields.length == 2)
                    .filter(fields -> fieldName.equals(fields[0]))
                    .map(fields -> fields[1])
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No such field: " + fieldName));
    }

    /**
     * @param file Number of bytes used to cache filesystem data, including tmpfs and shared memory.
     * @param sock Amount of memory used in network transmission buffers.
     * @param slab Amount of memory used for storing in-kernel data structures.
     * @param slabReclaimable Part of "slab" that might be reclaimed, such as dentries and inodes.
     * @param anon Amount of memory used in anonymous mappings such as brk(), sbrk(), and mmap(MAP_ANONYMOUS).
     */
    public record Stats(Size file, Size sock, Size slab, Size slabReclaimable, Size anon) {}
}
