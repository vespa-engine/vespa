package com.yahoo.metrics;

/**
 * @author gjoranv
 */
public enum SearchNodeMetrics implements VespaMetrics {

    SAMPLE1("sample1", Unit.RESPONSE, "sample1"),
    SAMPLE2("sample1", Unit.RESPONSE, "sample1");


    private final String name;
    private final Unit unit;
    private final String description;

    SearchNodeMetrics(String name, Unit unit, String description) {
        this.name = name;
        this.unit = unit;
        this.description = description;
    }

    public String baseName() {
        return name;
    }

    public Unit unit() {
        return unit;
    }

    public String description() {
        return description;
    }

}
