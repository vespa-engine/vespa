// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 */
public class VespaDomBuilderTest {

    final static String hosts = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<hosts>" +
            "  <host name=\"localhost\">" +
            "    <alias>node1</alias>" +
            "    <alias>node2</alias>" +
            "  </host>" +
            "</hosts>";

    final static String services = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<services>" +
            "  <config name=\"standard\">" +
            "    <basicStruct>" +
            "      <stringVal>default</stringVal>" +
            "    </basicStruct>" +
            "  </config> " +
            "  <config name=\"container.core.container-http\"><port><search>6745</search></port></config>" +
            "  <admin version=\"2.0\">" +
            "    <adminserver hostalias=\"node1\" />" +
            "  </admin>" +
            "  <container version=\"1.0\">" +
            "      <config name=\"standard\">" +
            "        <basicStruct>" +
            "          <stringVal>qrservers</stringVal>" +
            "        </basicStruct>" +
            "      </config> " +
            "    <nodes>\n" +
            "      <node hostalias=\"node1\"/>\n" +
            "    </nodes>\n" +
            "  </container>\n" +
            "</services>";

    final static String servicesWithNamespace = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<services>" +
            "  <config name=\"testnamespace\" namespace=\"foo\">" +
            "    <basicStruct>" +
            "      <stringVal>default</stringVal>" +
            "    </basicStruct>" +
            "  </config> " +
            "  <admin version=\"2.0\">" +
            "    <adminserver hostalias=\"node1\" />" +
            "  </admin>" +
            "</services>";

    final static String servicesWithNamespace2 = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
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
    public void testUserConfigsWithNamespace() throws Exception {
        VespaModel model = createModel(hosts, servicesWithNamespace);

        GenericConfig.GenericConfigBuilder builder = 
                new GenericConfig.GenericConfigBuilder(new ConfigDefinitionKey("testnamespace", "foo"), new ConfigPayloadBuilder());
        model.getConfig(builder, "admin");
        assertEquals(builder.getPayload().toString(), "{\n" + 
        		" \"basicStruct\": {\n" + 
        		"  \"stringVal\": \"default\"\n" + 
        		" }\n" + 
        		"}\n");
        
        model = createModel(hosts, servicesWithNamespace2);

        builder = new GenericConfig.GenericConfigBuilder(new ConfigDefinitionKey("testnamespace", "foo"), new ConfigPayloadBuilder());
        model.getConfig(builder, "admin");
        assertEquals(builder.getPayload().toString(), "{\n" + 
        		" \"basicStruct\": {\n" + 
        		"  \"stringVal\": \"default\"\n" + 
        		" }\n" + 
        		"}\n");
    }

    @Test
    public void testGetElement() {
        Element e = Xml.getElement(new StringReader("<searchchain><foo>sdf</foo></searchchain>"));
        assertEquals(e.getTagName(), "searchchain");
        assertEquals(XML.getChild(e, "foo").getTagName(), "foo");
        assertEquals(XML.getValue(XML.getChild(e, "foo")), "sdf");
    }

    @Test
    public void testHostSystem() {
        VespaModel model = createModel(hosts, services);
        HostSystem hostSystem = model.getHostSystem();
        assertThat(hostSystem.getHosts().size(), is(1));
        HostResource host = hostSystem.getHosts().get(0);
        assertThat(host, is(hostSystem.getHostByHostname(host.getHostname())));
        assertNotNull(hostSystem.getHost("node1"));
        assertThat(hostSystem.toString(), is("host '" + host.getHostname() + "'"));
    }

    private VespaModel createModel(String hosts, String services) {
       VespaModelCreatorWithMockPkg creator = new VespaModelCreatorWithMockPkg(hosts, services);
       return creator.create();
    }

}
