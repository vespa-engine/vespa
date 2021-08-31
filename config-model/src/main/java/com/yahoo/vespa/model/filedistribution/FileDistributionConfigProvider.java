// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.cloud.config.filedistribution.FiledistributorrpcConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.ConfigProxy;
import com.yahoo.vespa.model.Host;

public class FileDistributionConfigProvider extends AbstractConfigProducer<AbstractConfigProducer<?>> implements FiledistributorrpcConfig.Producer {

    private final Host host;

    public FileDistributionConfigProvider(AbstractConfigProducer<?> parent, Host host) {
        super(parent, host.getHostname());
        this.host = host;
    }

    @Override
    public void getConfig(FiledistributorrpcConfig.Builder builder) {
        builder.connectionspec("tcp/" + host.getHostname() + ":" + ConfigProxy.BASEPORT);
    }

}
