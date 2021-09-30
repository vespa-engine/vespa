// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.model;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.flags.FlagSource;

import java.util.Collections;
import java.util.Map;

/**
 * A config model that provides config containing information from all known tenants and applications.
 * 
 * @author Vegard Havdal
 */
public class SuperModelConfigProvider implements LbServicesConfig.Producer  {

    private final SuperModel superModel;
    private final LbServicesProducer lbProd;

    public SuperModelConfigProvider(SuperModel superModel, Zone zone, FlagSource flagSource) {
        this.superModel = superModel;
        this.lbProd = new LbServicesProducer(Collections.unmodifiableMap(superModel.getModelsPerTenant()), zone, flagSource);
    }

    public SuperModel getSuperModel() {
        return superModel;
    }

    public ConfigPayload getConfig(ConfigKey<?> configKey) {
        // TODO: Override not applied, but not really necessary here
        if (configKey.equals(new ConfigKey<>(LbServicesConfig.class, configKey.getConfigId())))  {
            LbServicesConfig.Builder builder = new LbServicesConfig.Builder();
            getConfig(builder);
            return ConfigPayload.fromInstance(new LbServicesConfig(builder));
        } else {
            throw new ConfigurationRuntimeException(configKey + " is not valid when asking for config from SuperModel");
        }
    }

    public Map<ApplicationId, ApplicationInfo> applicationModels() { return superModel.getModels(); }

    @Override
    public void getConfig(LbServicesConfig.Builder builder) {
        lbProd.getConfig(builder);
    }

}
