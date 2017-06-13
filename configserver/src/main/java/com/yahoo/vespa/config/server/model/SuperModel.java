// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.model;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.cloud.config.RoutingConfig;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.server.application.Application;

import java.util.Collections;
import java.util.Map;

/**
 * A config model that provides config containing information from all known tenants and applications.
 * 
 * @author vegardh
 * @since 5.9
 */
public class SuperModel implements LbServicesConfig.Producer, RoutingConfig.Producer  {

    private final Map<TenantName, Map<ApplicationId, Application>> models;
    private final LbServicesProducer lbProd;
    private final RoutingProducer zoneProd;
    
    public SuperModel(Map<TenantName, Map<ApplicationId, Application>> models, Zone zone) {
        this.models = models;
        this.lbProd = new LbServicesProducer(Collections.unmodifiableMap(models), zone);
        this.zoneProd = new RoutingProducer(Collections.unmodifiableMap(models));
    }

    public ConfigPayload getConfig(ConfigKey<?> configKey) {
        // TODO: Override not applied, but not really necessary here
        if (configKey.equals(new ConfigKey<>(LbServicesConfig.class, configKey.getConfigId())))  {
            LbServicesConfig.Builder builder = new LbServicesConfig.Builder();
            getConfig(builder);
            return ConfigPayload.fromInstance(new LbServicesConfig(builder));
        } else if (configKey.equals(new ConfigKey<>(RoutingConfig.class, configKey.getConfigId()))) {
            RoutingConfig.Builder builder = new RoutingConfig.Builder();
            getConfig(builder);
            return ConfigPayload.fromInstance(new RoutingConfig(builder));
        } else {
            throw new ConfigurationRuntimeException(configKey + " is not valid when asking for config from SuperModel");
        }
    }

    public Map<TenantName, Map<ApplicationId, Application>> applicationModels() { return models; }

    @Override
    public void getConfig(LbServicesConfig.Builder builder) {
        lbProd.getConfig(builder);
    }
    
    @Override
    public void getConfig(RoutingConfig.Builder builder) {
        zoneProd.getConfig(builder);
    }
    
    public <CONFIGTYPE extends ConfigInstance> CONFIGTYPE getConfig(Class<CONFIGTYPE> configClass, 
                                                                    ApplicationId applicationId,
                                                                    String configId) {
        TenantName tenant = applicationId.tenant();
        if (!models.containsKey(tenant)) {
            throw new IllegalArgumentException("Tenant " + tenant + " not found");
        }
        Map<ApplicationId, Application> applications = models.get(tenant);
        if (!applications.containsKey(applicationId)) {
            throw new IllegalArgumentException("Application id " + applicationId + " not found");
        }
        Application application = applications.get(applicationId);
        ConfigKey<CONFIGTYPE> key = new ConfigKey<>(configClass, configId);
        ConfigPayload payload = application.getModel().getConfig(key, null);
        return payload.toInstance(configClass, configId);
    }

}
