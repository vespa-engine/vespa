// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    public Optional<Pressure> readPressureIfExists() {
        return cgroup.readIfExists("memory.pressure")
                .map(fileContent ->
                        new Pressure(
                                readPressureField(fileContent, "some"),
                                readPressureField(fileContent, "full")
                        )
                );
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
     * Fetches the avg60 value from the specified type, i.e. "some" or "full".
     */
    private static Double readPressureField(String fileContent, String type) {
        var pattern = Pattern.compile(type + ".*avg60=(?<avg60>\\d+\\.\\d+).*");
        return Stream.of(fileContent.split("\n"))
                    .map(pattern::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group("avg60"))
                    .findFirst()
                    .map(Double::parseDouble)
                    .orElseThrow(() -> new IllegalArgumentException("No such field: " + type));
    }

    /**
     * @param file Number of bytes used to cache filesystem data, including tmpfs and shared memory.
     * @param sock Amount of memory used in network transmission buffers.
     * @param slab Amount of memory used for storing in-kernel data structures.
     * @param slabReclaimable Part of "slab" that might be reclaimed, such as dentries and inodes.
     * @param anon Amount of memory used in anonymous mappings such as brk(), sbrk(), and mmap(MAP_ANONYMOUS).
     */
    public record Stats(Size file, Size sock, Size slab, Size slabReclaimable, Size anon) {}

    /**
     * @param some The avg60 value of the "some" pressure level.
     * @param full The avg60 value of the "full" pressure level.
     */
    public record Pressure(double some, double full) {}
}
