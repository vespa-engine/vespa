// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.vespa.model.container.Identity;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class IdentityBuilderTest extends ContainerModelBuilderTestBase {
    @Test
    public void identity_config_produced() throws IOException, SAXException {
        Element clusterElem = DomBuilderTest.parse(
                "<jdisc id='default' version='1.0'>",
                "  <identity>",
                "    <domain>domain</domain>",
                "    <service>service</service>",
                "  </identity>",
                "</jdisc>");

        createModel(root, clusterElem);
        IdentityConfig identityConfig = root.getConfig(IdentityConfig.class, "default/component/" + Identity.CLASS);
        assertEquals("domain", identityConfig.domain());
        assertEquals("service", identityConfig.service());
    }
}
