// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import ai.vespa.cloud.ApplicationId;
import ai.vespa.cloud.Environment;
import ai.vespa.cloud.Zone;
import ai.vespa.cloud.ZoneInfo;
import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.ApplicationIdConfig;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.di.componentgraph.Provider;

/**
 * Provides information about the zone in which this container is running.
 * This is available and can be injected when running in a cloud environment.
 *
 * @author bratseth
 */
public class ZoneInfoProvider extends AbstractComponent implements Provider<ZoneInfo> {

    private final ZoneInfo instance;

    @Inject
    public ZoneInfoProvider(ConfigserverConfig csConfig, ApplicationIdConfig applicationIdConfig) {
        this.instance = new ZoneInfo(new ApplicationId(applicationIdConfig.tenant(),
                                                       applicationIdConfig.application(),
                                                       applicationIdConfig.instance()),
                                     new Zone(Environment.valueOf(csConfig.environment()), csConfig.region()));
    }

    @Override
    public ZoneInfo get() { return instance; }

}
