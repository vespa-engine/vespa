// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.nginx;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * File system paths used by Nginx.
 *
 * @author mpolden
 */
enum NginxPath {

    root("/opt/vespa/var/vespa-hosted/routing", null),
    config("nginxl4.conf", root),
    temporaryConfig("nginxl4.conf.tmp", root);

    public static final DateTimeFormatter ROTATED_SUFFIX_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss.SSS");

    private final String path;

    NginxPath(String path, NginxPath parent) {
        if (parent == null) {
            if (path.endsWith("/")) throw new IllegalArgumentException("Path should not end with '/', got '" + path + "'");
            this.path = path;
        } else {
            if (path.contains("/")) throw new IllegalArgumentException("Filename should not contain '/', got '" + path + "'");
            this.path = parent.path + "/" + path;
        }
    }

    /** Returns the path to this, bound to given file system */
    public Path in(FileSystem fileSystem) {
        return fileSystem.getPath(path);
    }

    /** Returns the rotated path of this with given instant, bound to given file system */
    public Path rotatedIn(FileSystem fileSystem, Instant instant) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        return fileSystem.getPath(path + "-" + ROTATED_SUFFIX_FORMAT.format(dateTime));
    }

}
