// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

/**
 * Provides information about a configserver instance.
 *
 * @author Tony Vaagenes
 */
public interface ConfigServerSpec {

    String getHostName();
    int getConfigServerPort();
    int getZooKeeperPort();

}
