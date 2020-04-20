// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

public interface DuperModelProvider {
    void registerListener(DuperModelListener listener);
}
