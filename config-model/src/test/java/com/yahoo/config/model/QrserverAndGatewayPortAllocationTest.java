// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerImpl;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests that qrserver is assigned port Defaults.getDefaults().vespaWebServicePort() even if there is a HTTP gateway configured earlier in
 * vespa-services.xml
 *
 * @author hmusum
 */
public class QrserverAndGatewayPortAllocationTest {

    @Test
    public void testPorts() throws IOException, SAXException {
        String appDir = "src/test/cfg/application/app_qrserverandgw/";
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg(appDir);
        VespaModel vespaModel = creator.create();
        List<ContainerImpl> qrservers = vespaModel.getContainerClusters().get("container").getContainers();
        assertThat(qrservers.size(), is(1));
        assertThat(qrservers.get(0).getSearchPort(), is(Container.BASEPORT));
    }

}
