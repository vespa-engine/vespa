// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.vespa.config.content.spooler.SpoolerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespaclient.config.FeederConfig;

/**
 * This model represents a spooler used for feeding documents to Vespa.
 *
 * @author <a href="mailto:gunnarga@yahoo-inc.com">Gunnar Gauslaa Bergem</a>
 * @author Vidar Larsen
 */
public class VespaSpoolerService extends AbstractService implements SpoolerConfig.Producer, FeederConfig.Producer {
    private static final long serialVersionUID = 1L;
    private VespaSpooler spooler;

    public VespaSpoolerService(AbstractConfigProducer parent, int index, VespaSpooler spooler) {
        super(parent, "spooler." + index);
        this.spooler = spooler;
        monitorService("spooler");
    }

    public int getPortCount() {
        return 0;
    }

    public String getStartupCommand() {
        return "exec vespaspooler "+getJvmArgs();
    }

    @Override
    public void getConfig(SpoolerConfig.Builder builder) {
        spooler.getConfig(builder);
    }

    @Override
    public void getConfig(FeederConfig.Builder builder) {
        spooler.getConfig(builder);
    }
}
