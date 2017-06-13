// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

/**
 * @author gjoranv
 */
public class PathDoesNotExistException extends RuntimeException {
    public final String path;

    PathDoesNotExistException(String path) {
        super("Path '" + path + "' does not exist.");
        this.path = path;
    }
}
