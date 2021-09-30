// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

/**
 * Access logging for requests
 *
 * @author bjorncs
 */
public interface RequestLog {

    void log(RequestLogEntry entry);

}
