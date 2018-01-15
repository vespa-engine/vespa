// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

/**
 * @author Simon Thoresen
 */
public interface BootstrapLoader {

    void init(String bundleLocation, boolean privileged) throws Exception;

    void start() throws Exception;

    void stop() throws Exception;

    void destroy();
}
