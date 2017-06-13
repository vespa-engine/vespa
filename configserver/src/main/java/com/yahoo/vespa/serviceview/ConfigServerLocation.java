// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview;

import com.yahoo.cloud.config.ConfigserverConfig;

/**
 * Wrapper for settings from the cloud.config.configserver config.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ConfigServerLocation {
    public final int restApiPort;

    public ConfigServerLocation(ConfigserverConfig configServer) {
        restApiPort = configServer.httpport();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ConfigServerLocation [restApiPort=").append(restApiPort).append("]");
        return builder.toString();
    }

}
