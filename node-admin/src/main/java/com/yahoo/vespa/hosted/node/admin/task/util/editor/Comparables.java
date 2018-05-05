// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

/**
 * @author hakon
 */
public class Comparables {
    public static <T extends Comparable<T>> T min(T first, T second) {
        if (first.compareTo(second) < 0) {
            return first;
        } else {
            return second;
        }
    }

    public static <T extends Comparable<T>> T max(T first, T second) {
        if (first.compareTo(second) < 0) {
            return second;
        } else {
            return first;
        }
    }
}
