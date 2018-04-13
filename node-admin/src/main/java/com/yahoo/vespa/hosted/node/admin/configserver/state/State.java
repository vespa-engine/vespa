// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.state;

/**
 * The /state/v1 REST API of the config server
 *
 * @author hakon
 */
public interface State {
    /** Issue GET on /state/v1/health */
    HealthResponse getHealth();
}
