// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.yolean.Exceptions;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads /proc/meminfo, see proc(5).
 *
 * @author hakon
 */
public class ProcMeminfoReader {
    private static final String PROC_MEMINFO = "/proc/meminfo";
    private static final Pattern MEM_TOTAL_PATTERN = Pattern.compile("MemTotal: *([0-9]+) kB");
    private static final Pattern MEM_AVAILABLE_PATTERN = Pattern.compile("MemAvailable: *([0-9]+) kB");

    private final FileSystem fileSystem;

    public ProcMeminfoReader(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public ProcMeminfo read() {
        return read(Exceptions.uncheck(() -> Files.readString(fileSystem.getPath(PROC_MEMINFO))));
    }

    static ProcMeminfo read(String meminfoContent) {
        return new ProcMeminfo(readKbGroup(meminfoContent, MEM_TOTAL_PATTERN),
                               readKbGroup(meminfoContent, MEM_AVAILABLE_PATTERN));
    }

    private static long readKbGroup(String string, Pattern pattern) {
        Matcher matcher = pattern.matcher(string);
        if (!matcher.find())
            throw new IllegalArgumentException(pattern + " did not match anything in " + PROC_MEMINFO);
        return Long.parseLong(matcher.group(1)) * 1024;
    }
}
