// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public interface BootstrapLoader {

    public void init(String bundleLocation, boolean privileged) throws Exception;

    public void start() throws Exception;

    public void stop() throws Exception;

    public void destroy();
}
