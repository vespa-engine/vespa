// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

public final class Dimension {
    private final DimensionId key;
    private final String value;
    public Dimension(DimensionId key, String value) {
        this.key = key;
        this.value = value;
    }
    public DimensionId key() { return key; }
    public String value() { return value; }
}
