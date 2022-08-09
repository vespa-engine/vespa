// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.application.Xml;
import com.yahoo.text.XML;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.config.GenericConfig;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author gjoranv
 */
public class VespaDomBuilderTest {

    private final static String hosts = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<hosts>" +
            "  <host name=\"localhost\">" +
            "    <alias>node1</alias>" +
            "    <alias>node2</alias>" +
            "  </host>" +
            "</hosts>";

    private final static String services = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<services>" +
            "  <config name=\"a.standard\">" +
            "    <basicStruct>" +
            "      <stringVal>default</stringVal>" +
            "    </basicStruct>" +
            "  </config> " +
            "  <config name=\"container.core.container-http\"><port><search>6745</search></port></config>" +
            "  <admin version=\"2.0\">" +
            "    <adminserver hostalias=\"node1\" />" +
            "  </admin>" +
            "  <container version=\"1.0\">" +
            "      <config name=\"a.standard\">" +
            "        <basicStruct>" +
            "          <stringVal>foo</stringVal>" +
            "        </basicStruct>" +
            "      </config> " +
            "    <nodes>\n" +
            "      <node hostalias=\"node1\"/>\n" +
            "    </nodes>\n" +
            "  </container>\n" +
            "</services>";

    private final static String servicesWithNamespace = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<services>" +
            "  <config name=\"foo.testnamespace\">" +
            "    <basicStruct>" +
            "      <stringVal>default</stringVal>" +
            "    </basicStruct>" +
            "  </config> " +
            "  <admin version=\"2.0\">" +
            "    <adminserver hostalias=\"node1\" />" +
            "  </admin>" +
            "</services>";


    @Test
    void testUserConfigsWithNamespace() {
        VespaModel model = createModel(hosts, servicesWithNamespace);

        GenericConfig.GenericConfigBuilder builder =
                new GenericConfig.GenericConfigBuilder(new ConfigDefinitionKey("testnamespace", "foo"), new ConfigPayloadBuilder());
        model.getConfig(builder, "admin");
        assertEquals("{\n" +
                "  \"basicStruct\": {\n" +
                "    \"stringVal\": \"default\"\n" +
                "  }\n" +
                "}\n", builder.getPayload().toString());
    }

    @Test
    void testGetElement() {
        Element e = Xml.getElement(new StringReader("<chain><foo>sdf</foo></chain>"));
        assertEquals(e.getTagName(), "chain");
        assertEquals(XML.getChild(e, "foo").getTagName(), "foo");
        assertEquals(XML.getValue(XML.getChild(e, "foo")), "sdf");
    }

    @Test
    void testHostSystem() {
        VespaModel model = createModel(hosts, services);
        HostSystem hostSystem = model.hostSystem();
        assertEquals(1, hostSystem.getHosts().size());
        HostResource host = hostSystem.getHosts().get(0);
        assertEquals(hostSystem.getHostByHostname(host.getHostname()), host);
        assertNotNull(hostSystem.getHost("node1"));
        assertEquals("hosts [" + host.getHostname() + "]", hostSystem.toString());
    }

    private VespaModel createModel(String hosts, String services) {
       VespaModelCreatorWithMockPkg creator = new VespaModelCreatorWithMockPkg(hosts, services);
       return creator.create();
    }

}
