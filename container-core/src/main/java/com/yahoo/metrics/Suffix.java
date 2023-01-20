package com.yahoo.metrics;

import java.util.EnumSet;

public enum Suffix {
    ninety_five_percentile("95percentile"),
    ninety_nine_percentile("99percentile"),
    average("average"),
    count("count"),
    last("last"),
    max("max"),
    min("min"),
    rate("rate"),
    sum("sum");

    private final String suffix;

    Suffix(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }

}
