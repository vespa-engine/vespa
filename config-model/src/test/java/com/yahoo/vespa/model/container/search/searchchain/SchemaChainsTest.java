// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.prelude.cluster.ClusterSearcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Test of search chains config
 * <p>TODO: examine the actual values in the configs.</p>
 *
 * @author Tony Vaagenes
 */
public class SchemaChainsTest extends SchemaChainsTestBase {

    private ChainsConfig chainsConfig;
    private ClusterConfig clusterConfig;

    @BeforeEach
    public void subscribe() {
        ChainsConfig.Builder chainsBuilder = new ChainsConfig.Builder();
        chainsBuilder = (ChainsConfig.Builder)root.getConfig(chainsBuilder, "searchchains");
        chainsConfig = new ChainsConfig(chainsBuilder);

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
    void require_that_source_chain_spec_id_is_namespaced_in_provider_id() {
        Source source = (Source) getSearchChains().allChains().getComponent("source:1@provider:1");
        assertEquals(ComponentId.fromString("provider:1"), source.getChainSpecification().componentId.getNamespace());
    }

    @Test
    void validateLocalProviderConfig() {
        assertEquals(2, clusterConfig.clusterId());
        assertEquals("cluster2", clusterConfig.clusterName());
    }

    private ChainsConfig.Chains findChain(String name) {
        for (ChainsConfig.Chains chain : chainsConfig.chains()) {
            if (name.equals(chain.id())) {
                return chain;
            }
        }
        return null;
    }

    private static boolean contains(Collection<ChainsConfig.Chains.Phases> phases, String name) {
        for (var phase : phases) {
            if (name.equals(phase.id())) {
                return true;
            }
        }
        return false;
    }

    private static void validateVespaPhasesChain(ChainsConfig.Chains chain) {
        assertNotNull(chain);
        assertEquals("vespaPhases", chain.id());
        assertEquals(5, chain.phases().size());
        assertTrue(contains(chain.phases(), PhaseNames.BACKEND));
        assertTrue(contains(chain.phases(), PhaseNames.BLENDED_RESULT));
        assertTrue(contains(chain.phases(), PhaseNames.RAW_QUERY));
        assertTrue(contains(chain.phases(), PhaseNames.TRANSFORMED_QUERY));
        assertTrue(contains(chain.phases(), PhaseNames.UNBLENDED_RESULT));
        assertTrue(chain.inherits().isEmpty());
        assertTrue(chain.components().isEmpty());
        assertTrue(chain.excludes().isEmpty());
        assertEquals(ChainsConfig.Chains.Type.SEARCH, chain.type());
    }

    private static void validateNativeChain(ChainsConfig.Chains chain) {
        assertNotNull(chain);
        assertEquals("native", chain.id());
        assertTrue(chain.phases().isEmpty());
        assertEquals(1, chain.inherits().size());
        assertEquals("vespaPhases", chain.inherits(0));
        assertEquals(2, chain.components().size());
        assertEquals("federation@native", chain.components(0));
        assertEquals("com.yahoo.prelude.statistics.StatisticsSearcher@native", chain.components(1));
        assertTrue(chain.excludes().isEmpty());
        assertEquals(ChainsConfig.Chains.Type.SEARCH, chain.type());
    }

    private static void validateVespaChain(ChainsConfig.Chains chain) {
        assertNotNull(chain);
        assertEquals("vespa", chain.id());
        assertTrue(chain.phases().isEmpty());
        assertEquals(1, chain.inherits().size());
        assertEquals("native", chain.inherits(0));
        assertEquals(10, chain.components().size());
        assertEquals("com.yahoo.prelude.querytransform.PhrasingSearcher@vespa", chain.components(0));
        assertEquals("com.yahoo.prelude.searcher.FieldCollapsingSearcher@vespa", chain.components(1));
        assertEquals("com.yahoo.search.yql.MinimalQueryInserter@vespa", chain.components(2));
        assertEquals("com.yahoo.search.yql.FieldFilter@vespa", chain.components(3));
        assertEquals("com.yahoo.prelude.searcher.JuniperSearcher@vespa", chain.components(4));
        assertEquals("com.yahoo.prelude.searcher.BlendingSearcher@vespa", chain.components(5));
        assertEquals("com.yahoo.prelude.searcher.PosSearcher@vespa", chain.components(6));
        assertEquals("com.yahoo.prelude.semantics.SemanticSearcher@vespa", chain.components(7));
        assertEquals("com.yahoo.search.grouping.GroupingQueryParser@vespa", chain.components(8));
        assertEquals("com.yahoo.search.querytransform.WeakAndReplacementSearcher@vespa", chain.components(9));
        assertTrue(chain.excludes().isEmpty());
        assertEquals(ChainsConfig.Chains.Type.SEARCH, chain.type());
    }

    @Test
    public void require_all_default_chains_are_correct() {
        assertEquals(61, chainsConfig.components().size());
        assertEquals(10, chainsConfig.chains().size());
        validateVespaPhasesChain(findChain("vespaPhases"));
        validateNativeChain(findChain("native"));
        validateVespaChain(findChain("vespa"));
    }
}
