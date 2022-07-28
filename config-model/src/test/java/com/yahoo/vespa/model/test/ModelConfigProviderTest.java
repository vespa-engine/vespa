// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test HostSystem
 *
 * @author hmusum
 */
public class ModelConfigProviderTest {

    /**
     * Get the config via ConfigInstance based API, by getting whole config
     */
    @Test
    void testGetModelConfig() {
        VespaModel vespaModel = new VespaModelCreatorWithFilePkg("src/test/cfg/admin/adminconfig20").create();
        ModelConfig config = vespaModel.getConfig(ModelConfig.class, "");
        assertEquals(config.hosts().size(), 1);
        ModelConfig.Hosts localhost = config.hosts(0); //Actually set to hostname.
        int numLogservers = 0;
        int numSlobroks = 0;
        for (ModelConfig.Hosts.Services service : localhost.services()) {
            if ("logserver".equals(service.type())) {
                numLogservers++;
            }
            if ("slobrok".equals(service.type())) {
                numSlobroks++;
            }
        }
        assertEquals(1, numLogservers);
        assertEquals(2, numSlobroks);
    }

}
