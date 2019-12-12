// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.client.dsl;


public final class G {

    public static Group all(IGroupOperation... ops) {
        return new Group("all", ops);
    }

    public static Group each(IGroupOperation... ops) {
        return new Group("each", ops);
    }

    public static GroupOperation group(String expr) {
        return new GroupOperation("group", expr);
    }

    public static GroupOperation maxRtn(int max) {
        return new GroupOperation("max", max);
    }

    public static GroupOperation order(String expr) {
        return new GroupOperation("order", expr);
    }

    public static GroupOperation output(Aggregator... aggrs) {
        return new GroupOperation("output", aggrs);
    }

    public static Aggregator max(int max) {
        return new Aggregator("max", max);
    }

    public static Aggregator summary() {
        return new Aggregator("summary");
    }

    public static Aggregator count() {
        return new Aggregator("count");
    }

    public static Aggregator summary(String summaryClass) {
        return new Aggregator("summary", summaryClass);
    }
}
