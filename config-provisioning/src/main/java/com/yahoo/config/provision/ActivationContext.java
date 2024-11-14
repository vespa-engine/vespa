// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * The context passed to the node repo when activating a node.
 *
 * @author bratseth
 */
public class ActivationContext {

    private final long generation;
    private final boolean isBootstrap;

    public ActivationContext(long generation, boolean isBootstrap) {
        this.generation = generation;
        this.isBootstrap = isBootstrap;
    }

    /** Returns the application config generation we are activating */
    public long generation() { return generation; }

    /** Returns true if this deployment is done to bootstrap the config server */
    public boolean isBootstrap() { return isBootstrap; }
}
