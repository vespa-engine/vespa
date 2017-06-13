// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

public interface EventLogger {
    public void value(String name, double value);
    public void count(String name, long value);
}
