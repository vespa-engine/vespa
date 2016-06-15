// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.vespa.config.content.spooler.SpoolerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespaclient.config.FeederConfig;

/**
 * This model represents a config producer for spooler used for feeding documents to Vespa.
 *
 * @author <a href="mailto:gunnarga@yahoo-inc.com">Gunnar Gauslaa Bergem</a>
 * @author Vidar Larsen
 */
public class VespaSpoolerProducer extends AbstractConfigProducer implements SpoolerConfig.Producer, FeederConfig.Producer {
    private static final long serialVersionUID = 1L;
    private VespaSpooler spoolerConfig;

    public VespaSpoolerProducer(AbstractConfigProducer parent, String configId, VespaSpooler spooler) {
        super(parent, configId);
        spoolerConfig = spooler;
    }

    @Override
    public void getConfig(SpoolerConfig.Builder builder) {
        spoolerConfig.getConfig(builder);
    }

    @Override
    public void getConfig(FeederConfig.Builder builder) {
        spoolerConfig.getConfig(builder);
    }
}
