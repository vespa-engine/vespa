// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.logging;

public interface Logger {

    default LoggerEntry.Builder newEntry() {
        return new LoggerEntry.Builder(this);
    }

    boolean send(LoggerEntry entry);

}
