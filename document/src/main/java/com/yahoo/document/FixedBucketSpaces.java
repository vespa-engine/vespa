// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

/**
 * Minimal repository of bucket spaces hard coded for default and global distributions.
 *
 * @author geirst
 */
public class FixedBucketSpaces {
    public static String defaultSpace() {
        return "default";
    }
    public static String globalSpace() {
        return "global";
    }
}
