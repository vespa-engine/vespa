// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.api.annotations.Beta;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.VespaModel;

import java.util.List;

/**
 * Test utility class that provides many methods for inspecting the state of a completely built model
 *
 * @author Ulf Lilleengen
 */
@Beta
public class TestRoot {
    private final VespaModel model;
    TestRoot(VespaModel model) {
        this.model = model;
    }

    /**
     * Get a list of all config models of a particular type.
     *
     * @param clazz The class of the models to find.
     * @return A list of models of given type.
     */
    public <MODEL extends ConfigModel> List<MODEL> getConfigModels(Class<MODEL> clazz) {
        return model.configModelRepo().getModels(clazz);
    }

    /**
     * Ask model to populate builder with config for a given config id. This method gives the same config as the
     * configserver would return to its clients.
     * @param builder The builder to populate
     * @param configId The config id of the producer to ask for config.
     * @return the same builder.
     */
    public <BUILDER extends ConfigInstance.Builder> BUILDER getConfig(BUILDER builder, String configId) {
        return (BUILDER) model.getConfig(builder, configId);
    }

    /**
     * Request config of a given type and id. This method gives the same config as the configserver would return to
     * its clients.
     *
     * @param clazz Type of config to request.
     * @param configId The config id of the producer to ask for config.
     * @return A config object of the appropriate type with config values set.
     */
    public <CONFIGTYPE extends ConfigInstance> CONFIGTYPE getConfig(Class<CONFIGTYPE> clazz, String configId) {
        return model.getConfig(clazz, configId);
    }

    /**
     * Retrieve the hosts available in this model. Useful to verify that hostnames are set correctly etc.
     *
     * @return A list of hosts.
     */
    public List<HostResource> getHosts() {
        return model.hostSystem().getHosts();
    }
}
