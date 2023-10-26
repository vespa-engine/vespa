// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

import java.util.Map;

public class NoMetricReporter implements MetricReporter {

    @Override
    public void set(String s, Number number, Context context) {}

    @Override
    public void add(String s, Number number, Context context) {}

    @Override
    public Context createContext(Map<String, ?> stringMap) { return null; }

}
