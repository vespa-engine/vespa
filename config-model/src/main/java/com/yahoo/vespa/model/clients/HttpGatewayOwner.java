// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.config.subscription.ConfigInstanceUtil;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespaclient.config.FeederConfig;

public class HttpGatewayOwner extends AbstractConfigProducer implements FeederConfig.Producer {
    private final FeederConfig.Builder feederConfig;

    public HttpGatewayOwner(AbstractConfigProducer parent, FeederConfig.Builder feederConfig) {
        super(parent, "gateways");
        this.feederConfig = feederConfig;
    }

    @Override
    public void getConfig(FeederConfig.Builder builder) {
        ConfigInstanceUtil.setValues(builder, feederConfig);
    }
}
