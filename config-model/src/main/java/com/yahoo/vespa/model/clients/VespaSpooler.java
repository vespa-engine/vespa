// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.vespa.config.content.spooler.SpoolerConfig;
import com.yahoo.config.subscription.ConfigInstanceUtil;
import com.yahoo.vespaclient.config.FeederConfig;

/**
 * Holds configuration for VespaSpoolers. Actual services use VespaSpoolerService,
 * while virtual services can be generated for external spoolers (VespaSpoolerProducer).
 *
 * @author <a href="mailto:thomasg@yahoo-inc.com">Gunnar Gauslaa Bergem</a>
 * @author Vidar Larsen
 */
public class VespaSpooler {
    private final SpoolerConfig.Builder spoolConfig;
    private final FeederConfig.Builder feederConfig;

    public VespaSpooler(FeederConfig.Builder feederConfig, SpoolerConfig.Builder spoolConfig) {
        this.feederConfig = feederConfig;
        this.spoolConfig = spoolConfig;
    }

    public void getConfig(SpoolerConfig.Builder builder) {
        ConfigInstanceUtil.setValues(builder, spoolConfig);
    }

    public void getConfig(FeederConfig.Builder builder) {
        ConfigInstanceUtil.setValues(builder, feederConfig);
    }
}
