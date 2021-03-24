// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview;

import ai.vespa.util.http.VespaClientBuilderFactory;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;

/**
 * Wrapper for settings from the cloud.config.configserver config.
 *
 * @author Steinar Knutsen
 */
public class ConfigServerLocation extends AbstractComponent {

    final int restApiPort;
    // The client factory must be owned by a component as StateResource is instantiated per request
    @SuppressWarnings("removal")
    final VespaClientBuilderFactory clientBuilderFactory = new VespaClientBuilderFactory();

    @Inject
    public ConfigServerLocation(ConfigserverConfig configServer) {
        restApiPort = configServer.httpport();
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ConfigServerLocation [restApiPort=").append(restApiPort).append("]");
        return builder.toString();
    }

    @Override
    public void deconstruct() {
        clientBuilderFactory.close();
    }
}
