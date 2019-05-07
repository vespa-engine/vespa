// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

/**
 * Provides information about a configserver instance.
 *
 * @author Tony Vaagenes
 */
public interface ConfigServerSpec {

    String getHostName();
    int getConfigServerPort();
    // TODO: Remove when latest model version in use is 7.47
    default int getHttpPort() { return getConfigServerPort() + 1; }
    int getZooKeeperPort();

}
