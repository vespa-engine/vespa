// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests that qrserver is assigned port Defaults.getDefaults().vespaWebServicePort() even if there is a HTTP gateway configured earlier in
 * vespa-services.xml
 *
 * @author hmusum
 */
public class QrserverAndGatewayPortAllocationTest {

    @Test
    public void testPorts() {
        String appDir = "src/test/cfg/application/app_qrserverandgw/";
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg(appDir);
        VespaModel vespaModel = creator.create();
        List<ApplicationContainer> qrservers = vespaModel.getContainerClusters().get("container").getContainers();
        assertEquals(1, qrservers.size());
        assertEquals(Container.BASEPORT, qrservers.get(0).getSearchPort());
    }

}
