// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.search.federation.ProviderConfig;
import com.yahoo.vespa.model.builder.xml.dom.chains.ComponentsBuilder;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import com.yahoo.vespa.model.container.search.searchchain.HttpProvider;
import com.yahoo.vespa.model.container.search.searchchain.HttpProviderSearcher;
import com.yahoo.vespa.model.container.search.searchchain.Provider;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.HashMap;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author gjoranv
 */
public class DomProviderBuilderTest extends DomBuilderTest {

    private static final Element noProxy = parse(
            "<provider id='yca-provider' type='vespa' yca-application-id='my-app'>",
            "  <nodes>",
            "    <node host='sourcehost' port='12'/>",
            "  </nodes>",
            "</provider>");

    private static final Element defaultProxy = parse(
            "<provider id='yca-provider' type='vespa' yca-application-id='my-app'>",
            "  <yca-proxy/>",
            "  <nodes>",
            "    <node host='sourcehost' port='12'/>",
            "  </nodes>",
            "</provider>");

    private static final Element proprietaryProxy = parse(
            "<provider id='yca-provider' type='vespa' yca-application-id='my-app'>",
            "  <yca-proxy host='my-host' port='80'/>",
            "  <nodes>",
            "    <node host='sourcehost' port='12'/>",
            "  </nodes>",
            "</provider>");

    private static final Element illegal_proxyWithoutId= parse(
            "<provider id='yca-provider' type='vespa'>",
            "  <yca-proxy host='my-host' port='80'/>",
            "  <nodes>",
            "    <node host='sourcehost' port='12'/>",
            "  </nodes>",
            "</provider>");

    private Provider provider;

    @Test
    public void testYcaConfig_noProxy() {
        provider = new DomProviderBuilder(new HashMap<String, ComponentsBuilder.ComponentType>()).doBuild(root, noProxy);

        ChainedComponent providerSearcher = provider.getInnerComponents().iterator().next();
        assertThat(providerSearcher, instanceOf(HttpProviderSearcher.class));

        ProviderConfig.Builder providerBuilder = new ProviderConfig.Builder();
        ((HttpProvider)provider).getConfig(providerBuilder);
        ProviderConfig providerConfig = new ProviderConfig(providerBuilder);
        assertThat(providerConfig.yca().applicationId(), is("my-app"));
        assertThat(providerConfig.yca().useProxy(), is(false));
    }

    @Test
    public void testYcaConfig_defaultProxy() {
        provider = new DomProviderBuilder(new HashMap<String, ComponentsBuilder.ComponentType>()).doBuild(root, defaultProxy);

        ProviderConfig.Builder providerBuilder = new ProviderConfig.Builder();
        ((HttpProvider)provider).getConfig(providerBuilder);
        ProviderConfig providerConfig = new ProviderConfig(providerBuilder);

        assertThat(providerConfig.yca().applicationId(), is("my-app"));
        assertThat(providerConfig.yca().useProxy(), is(true));
        assertThat(providerConfig.yca().host(), is("yca-proxy.corp.yahoo.com"));  // default from def-file
        assertThat(providerConfig.yca().port(), is(3128));  // default from def-file
    }

    @Test
    public void testYcaConfig_proprietaryProxy() {
        provider = new DomProviderBuilder(new HashMap<String, ComponentsBuilder.ComponentType>()).doBuild(root, proprietaryProxy);

        ProviderConfig.Builder providerBuilder = new ProviderConfig.Builder();
        ((HttpProvider)provider).getConfig(providerBuilder);
        ProviderConfig providerConfig = new ProviderConfig(providerBuilder);

        assertThat(providerConfig.yca().applicationId(), is("my-app"));
        assertThat(providerConfig.yca().useProxy(), is(true));
        assertThat(providerConfig.yca().host(), is("my-host"));
        assertThat(providerConfig.yca().port(), is(80));
    }

    @Test
    public void testFail_ycaProxyWithoutId() {
        try {
            provider = new DomProviderBuilder(new HashMap<String, ComponentsBuilder.ComponentType>()).doBuild(root, illegal_proxyWithoutId);
            fail("Expected exception upon illegal xml.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Provider 'yca-provider' must have a YCA application ID, since a YCA proxy is given"));
        }
    }

}
