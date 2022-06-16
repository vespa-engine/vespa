// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.prelude.cluster.ClusterSearcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import static org.junit.Assert.assertEquals;


/**
 * Test of search chains config
 * <p>TODO: examine the actual values in the configs.</p>
 *
 * @author Tony Vaagenes
 */
public class SchemaChainsTest extends SchemaChainsTestBase {

    private ClusterConfig clusterConfig;

    @Before
    public void subscribe() {
        ClusterConfig.Builder clusterBuilder = new ClusterConfig.Builder();
        clusterBuilder = (ClusterConfig.Builder)root.getConfig(clusterBuilder, "searchchains/chain/cluster2/component/" + ClusterSearcher.class.getName());
        clusterConfig = new ClusterConfig(clusterBuilder);
    }


    @Override
    Element servicesXml() {
        return parse(
            "<search>",
            "  <searcher id='searcher:1' classId='classId1' />",

            "  <provider id='provider:1' inherits='parentChain1 parentChain2' excludes='ExcludedSearcher1 ExcludedSearcher2'",
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

            "  <provider id='vespa-provider'>",
            "    <nodes>",
            "      <node host='localhost' port='" + Defaults.getDefaults().vespaWebServicePort() + "' />",
            "   </nodes>",
            "     <config name='search.federation.provider'>",
            "       <queryType>PROGRAMMATIC</queryType>",
            "    </config>",
            "  </provider>",
            "</search>");
    }

    private SearchChains getSearchChains() {
        return (SearchChains) root.getChildren().get("searchchains");
    }

    @Test
    public void require_that_source_chain_spec_id_is_namespaced_in_provider_id() {
        Source source = (Source) getSearchChains().allChains().getComponent("source:1@provider:1");
        assertEquals(ComponentId.fromString("provider:1"), source.getChainSpecification().componentId.getNamespace());
    }

    @Test
    public void validateLocalProviderConfig() {
        assertEquals(2, clusterConfig.clusterId());
        assertEquals("cluster2", clusterConfig.clusterName());
    }

}
