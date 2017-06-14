// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Spec saved from config. If metricSetChildren has content, metric pointed
 * to is a metric set.
 */
class ConsumerSpec {
    Set<String> includedMetrics = new HashSet<String>();

    public boolean contains(Metric m) {
        return includedMetrics.contains(m.getPath());
    }

    public void register(String path) {
        StringTokenizer tokenizer = new StringTokenizer(path, ".");

        String total = "";

        while (tokenizer.hasMoreTokens()) {
            if (!total.isEmpty()) {
                total += ".";
            }
            total += tokenizer.nextToken();
            includedMetrics.add(total);
        }
    }
}
