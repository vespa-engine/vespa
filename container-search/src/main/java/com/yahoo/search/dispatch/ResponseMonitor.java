// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

/**
 * Classes implementing ResponseMonitor can be informed by monitored objects
 * that a response is available for processing. The responseAvailable method
 * must be thread-safe.
 *
 * @author ollivir
 */
public interface ResponseMonitor<T> {

    void responseAvailable(T from);

}
