// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.cloud.config.filedistribution.FiledistributorrpcConfig;
import com.yahoo.cloud.config.filedistribution.FilereferencesConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * @author hmusum
 * <p>
 * Dummy file distribution config producer, needed for serving file distribution config when there is no FiledistributorService.
 */
public class DummyFileDistributionConfigProducer extends AbstractConfigProducer implements
        FiledistributorrpcConfig.Producer,
        FilereferencesConfig.Producer {

    private final FileDistributionConfigProvider configProvider;

    public DummyFileDistributionConfigProducer(AbstractConfigProducer parent,
                                               String hostname,
                                               FileDistributionConfigProvider configProvider) {
        super(parent, hostname);
        this.configProvider = configProvider;
    }

    @Override
    public void getConfig(FiledistributorrpcConfig.Builder builder) {
        configProvider.getConfig(builder);
    }

    @Override
    public void getConfig(FilereferencesConfig.Builder builder) {
        configProvider.getConfig(builder);
    }
}
