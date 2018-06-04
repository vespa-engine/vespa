// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.vespa.model.AbstractService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */

public class StandaloneContainerTest {
    private static final String PLAIN_XML = "<container version=\"1.0\" />";

    @Test
    public void container_is_allowed_root_element() throws Exception {
        StandaloneContainer.withContainerModel(PLAIN_XML, root -> null);
    }

    @Test
    public void services_is_allowed_root_element() throws Exception {
        String servicesXml = "<services>" + //
                "<container version=\"1.0\" />" + //
                "</services>";

        StandaloneContainer.withContainerModel(servicesXml, root -> null);
    }

    @Test(expected = Exception.class)
    public void multiple_container_elements_cannot_be_deployed() throws Exception {
        String twoContainersXml = "<services>" + //
                "<container id=\"container-1\" version=\"1.0\" />" + //
                "<container id=\"container-2\" version=\"1.0\" />" + //
                "</services>";

        StandaloneContainer.withContainerModel(twoContainersXml, root -> null);
    }

    @Test
    public void application_preprocessor_is_run() throws Exception {
        String servicesXml = "<services xmlns:preprocess=\"properties\">" + //
                "<preprocess:properties>" + //
                "<container_id>container-1</container_id>" + //
                "</preprocess:properties>" + //
                "<container id=\"${container_id}\" version=\"1.0\" />" + //
                "</services>";

        StandaloneContainer.withContainerModel(servicesXml, root -> {
            assertTrue(root.getConfigProducer("container-1/standalone").isPresent());
            return null;
        });
    }

    @Test
    public void no_default_ports_are_enabled_when_using_http() throws Exception {
        String xml = "<jdisc version=\"1.0\">" + //
                "<http>" + //
                "<server port=\"4000\" id=\"server1\" />" + //
                "</http>" + //
                "</jdisc>";

        StandaloneContainer.withContainerModel(xml, root -> {
            AbstractService container = (AbstractService) root.getConfigProducer("jdisc/standalone").get();
            System.out.println("portCnt: " + container.getPortCount());
            System.out.println("numPorts: " + container.getNumPortsAllocated());
            assertEquals(1, container.getNumPortsAllocated());
            return null;
        });
    }
}
