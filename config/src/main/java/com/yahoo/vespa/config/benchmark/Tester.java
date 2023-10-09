// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.benchmark;

import java.util.Map;

/**
 * Tester interface for loadable test runners.
 */
public interface Tester {
    void subscribe();
    boolean fetch();
    boolean verify(Map<String, Map<String, String>> expected, long generation);
    void close();
}
