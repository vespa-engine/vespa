// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.config.PortOverridesConfig;

/**
 * Holds PortsOverrideConfig for the current container.
 * HttpServerConfigProvider can't depend on PortsOverrideConfig directly,
 * since it's config producer is not under the container in the model.
 * @author tonytv
 */
public final class ContainerPortsOverride extends AbstractComponent {
    public final PortOverridesConfig config;

    public ContainerPortsOverride(PortOverridesConfig portOverridesConfigConfig) {
        this.config = portOverridesConfigConfig;
    }
}
