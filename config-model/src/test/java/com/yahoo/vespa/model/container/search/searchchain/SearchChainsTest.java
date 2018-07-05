// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.prelude.cluster.ClusterSearcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.federation.ProviderConfig;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


/**
 * Test of search chains config
 * <p>TODO: examine the actual values in the configs.</p>
 * @author Tony Vaagenes
 */
public class SearchChainsTest extends SearchChainsTestBase {
    private ChainsConfig chainsConfig;
    private ProviderConfig providerConfig;
    private ClusterConfig clusterConfig;

    @Before
    public void subscribe() {
        ChainsConfig.Builder chainsBuilder = new ChainsConfig.Builder();
        chainsBuilder = (ChainsConfig.Builder)root.getConfig(chainsBuilder, "searchchains");
        chainsConfig = new ChainsConfig(chainsBuilder);

        ProviderConfig.Builder providerBuilder = new ProviderConfig.Builder();
        providerBuilder = (ProviderConfig.Builder)root.getConfig(providerBuilder, "searchchains/chain/provider:1/component/com.yahoo.search.federation.vespa.VespaSearcher");
        providerConfig = new ProviderConfig(providerBuilder);

        ClusterConfig.Builder clusterBuilder = new ClusterConfig.Builder();
        clusterBuilder = (ClusterConfig.Builder)root.getConfig(clusterBuilder, "searchchains/chain/cluster2/component/" + ClusterSearcher.class.getName());
        clusterConfig = new ClusterConfig(clusterBuilder);
    }


    @Override
    Element servicesXml() {
        return parse(
            "<searchchains>",
            "  <searcher id='searcher:1' classId='classId1' />",

            "  <provider id='provider:1' type='vespa' inherits='parentChain1 parentChain2' excludes='ExcludedSearcher1 ExcludedSearcher2'",
            "             cacheweight='2.3'>",
            "    <federationoptions optional='true' timeout='2.3 s' />",
            "    <nodes>",
            "      <node host='sourcehost1' port='12'/>",
            "      <node host='sourcehost2' port='34'/>",
            "    </nodes>",

            "    <source id='source:1' inherits='parentChain3 parentChain4' excludes='ExcludedSearcher3 ExcludedSearcher4'>",
            "      <federationoptions timeout='12 ms' />",
            "    </source>",
            "    <source id='source:2' />",
            "  </provider>",

            "  <provider id='provider:2' type='local' cluster='cluster1' />",
            "  <provider id='provider:3' />",

            "  <provider id='vespa-provider' type='vespa' >",
            "    <nodes>",
            "      <node host='localhost' port='" + Defaults.getDefaults().vespaWebServicePort() + "' />",
            "   </nodes>",
            "     <config name='search.federation.provider'>",
            "       <queryType>PROGRAMMATIC</queryType>",
            "    </config>",
            "  </provider>",

            "  <searchchain id='default:99'>",
            "    <federation id='federation:98' provides='provide_federation' before='p1 p2' after='s1 s2'>",
            "      <source id='source:1'>",
            "        <federationoptions optional='false' />",
            "      </source>",
            "    </federation>",
            "  </searchchain>",

            "  <searchchain id='parentChain1' />",
            "  <searchchain id='parentChain2' />",
            "  <searchchain id='parentChain3' />",
            "  <searchchain id='parentChain4' />",
            "</searchchains>");
    }

    @Test
    public void require_vespa_searcher_inside_vespa_provider() {
        SearchChains searchchains = getSearchChains();
        SearchChain vespaProvider = searchchains.allChains().getComponent("vespa-provider");
        Searcher<?> vespaSearcher = vespaProvider.getInnerComponents().iterator().next();
        assertThat(vespaSearcher, instanceOf(HttpProviderSearcher.class));
    }

    private SearchChains getSearchChains() {
        return (SearchChains) root.getChildren().get("searchchains");
    }

    @Test
    public void require_user_config_for_vespa_searcher_works() {
        assertEquals(root.getConfig(ProviderConfig.class, "searchchains/chain/vespa-provider/component/com.yahoo.search.federation.vespa.VespaSearcher").
            queryType(), ProviderConfig.QueryType.PROGRAMMATIC); 
    }

    @Test
    public void require_that_source_chain_spec_id_is_namespaced_in_provider_id() {
        Source source = (Source) getSearchChains().allChains().getComponent("source:1@provider:1");
        assertThat(source.getChainSpecification().componentId.getNamespace(), is(ComponentId.fromString("provider:1")));
    }

    @Test
    public void validateHttpProviderConfig() {
        assertNotNull(providerConfig);
    }

    @Test
    public void validateLocalProviderConfig() {
        assertEquals(2, clusterConfig.clusterId());
        assertEquals("cluster2", clusterConfig.clusterName());
    }

    public static boolean verifyChainExists(List<ChainsConfig.Chains> chains, String componentId) {
        for (ChainsConfig.Chains c : chains) {
            if (c.id().equals(componentId)) return true;
        }
        return false;
    }
}
