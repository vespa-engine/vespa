// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients.test;

import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.QrConfig;
import com.yahoo.container.QrConfig.Builder;
import com.yahoo.net.HostName;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Gunnar Gauslaa Bergem
 */
public class Gateway20TestCase {

    private static String hostname = HostName.getLocalhost();  // Using the same way of getting hostname as filedistribution model

    @Test
    public void testSimpleDocprocV3() throws Exception {
        VespaModel model = new VespaModelCreatorWithFilePkg("src/test/cfg/clients/simpleconfig.v2.docprocv3").create();
        QrConfig qrConfig = new QrConfig((Builder) model.getConfig(new QrConfig.Builder(), "container/container.0"));
        assertEquals(qrConfig.rpc().enabled(), true);
        assertEquals("filedistribution/" + hostname, qrConfig.filedistributor().configid());
        assertEquals("container.container.0", qrConfig.discriminator());

        ComponentsConfig componentsConfig = new ComponentsConfig((ComponentsConfig.Builder) model.getConfig(new ComponentsConfig.Builder(), "container/container.0"));
        ArrayList<String> components = new ArrayList<>();
        for (ComponentsConfig.Components component : componentsConfig.components()) {
            components.add(component.id());
        }
        List<String> expectedComponents = Arrays.asList("com.yahoo.docproc.jdisc.DocumentProcessingHandler",
                "com.yahoo.feedhandler.VespaFeedHandler",
                "com.yahoo.feedhandler.VespaFeedHandlerCompatibility",
                "com.yahoo.feedhandler.VespaFeedHandlerGet",
                "com.yahoo.feedhandler.VespaFeedHandlerRemove",
                "com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation",
                "com.yahoo.feedhandler.VespaFeedHandlerStatus",
                "com.yahoo.feedhandler.VespaFeedHandlerVisit",
                "com.yahoo.search.handler.SearchHandler",
                "com.yahoo.container.jdisc.state.StateHandler");
        assertTrue(components.containsAll(expectedComponents));
    }

    @Test
    public void testAdvanced() throws Exception {
        VespaModel model = new VespaModelCreatorWithFilePkg("src/test/cfg/clients/advancedconfig.v2").create();

        QrConfig qrConfig = new QrConfig((Builder) model.getConfig(new QrConfig.Builder(), "container/container.0"));
        assertEquals(qrConfig.rpc().enabled(), true);
        assertEquals(qrConfig.filedistributor().configid(), "filedistribution/" + hostname);
        assertEquals("container.container.0", qrConfig.discriminator());

        qrConfig = new QrConfig((Builder) model.getConfig(new QrConfig.Builder(), "container/container.0"));
        assertEquals(qrConfig.rpc().enabled(), true);
        assertEquals(qrConfig.filedistributor().configid(), "filedistribution/" + hostname);
    }

}
