// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

/**
 * @author mortent
 */
public interface ConnectionLog {
    void log(ConnectionLogEntry connectionLogEntry);
}
