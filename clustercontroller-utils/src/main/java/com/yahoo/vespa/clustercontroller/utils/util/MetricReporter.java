// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

/**
 * Wrapper for current jdisc metrics, such that applications can report metrics without depending on
 * the whole container. The apps project will provide an implementation of this interface that
 * reports on to injected jdisc implementation.
 */
public interface MetricReporter {

    void set(java.lang.String s, java.lang.Number number, Context context);

    void add(java.lang.String s, java.lang.Number number, Context context);

    Context createContext(java.util.Map<java.lang.String,?> dimensions);

    interface Context {
    }

}
