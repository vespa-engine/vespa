// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

/**
 * Provides information about a configserver instance.
 *
 * @author tonytv
 */
public interface ConfigServerSpec {
    public String getHostName();
    public int getConfigServerPort();
    public int getHttpPort();
    public int getZooKeeperPort();
}
