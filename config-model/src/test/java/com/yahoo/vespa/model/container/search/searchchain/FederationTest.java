// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.search.federation.FederationConfig;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.List;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test generated config for federation.
 * @author Tony Vaagenes
 */
public class FederationTest extends SchemaChainsTestBase {
    @Override
    Element servicesXml() {
        return parse(
                "<searchchains>",
                "  <searchchain id='federation1'>",
                "    <federation id='federationSearcher1'>",
                "      <source id='source1'>",
                "        <federationoptions optional='false' />",
                "      </source>",
                "    </federation>",
                "  </searchchain>",


                "  <provider id='provider1'>",
                "    <federationoptions optional='true' timeout='2.3 s' />",

                "    <source id='source1'>",
                "      <federationoptions timeout='12 ms' />",
                "    </source>",
                "    <source id='source2' />",
                "    <source id='sourceCommon' />",
                "  </provider>",

                "  <provider id='provider2' type='local' cluster='cluster1' />",

                "  <provider id='provider3'>",
                "    <source idref='sourceCommon' />",
                "  </provider>",

                "  <searchchain id='parentChain1' />",
                "</searchchains>");
    }


    @Test
    void validateNativeDefaultTargets() {
        FederationConfig.Builder fb = new FederationConfig.Builder();
        root.getConfig(fb, "searchchains/chain/native/component/federation");
        FederationConfig config = new FederationConfig(fb);

        for (FederationConfig.Target target : config.target()) {
            String failMessage = "Failed for target " + target.id();

            if (target.id().startsWith("source")) {
                assertTrue(target.useByDefault(), failMessage);
            } else {
                assertFalse(target.useByDefault(), failMessage);
            }
        }

        assertEquals(5, config.target().size());
        assertUseByDefault(config, "source1", false);
        assertUseByDefault(config, "source2", false);

        assertUseByDefault(config, "provider2", true);
        assertUseByDefault(config, "cluster2", true);

        assertUseByDefault(config, "sourceCommon", "provider1", false);
        assertUseByDefault(config, "sourceCommon", "provider3", false);

    }

    private void assertUseByDefault(FederationConfig config, String sourceName, String providerName,
                                    boolean expectedValue) {

        FederationConfig.Target target = getTarget(config.target(), sourceName);
        FederationConfig.Target.SearchChain searchChain = getProvider(target, providerName);
        assertEquals(expectedValue, searchChain.useByDefault());
    }

    private FederationConfig.Target.SearchChain getProvider(FederationConfig.Target target, String providerName) {
        for (FederationConfig.Target.SearchChain searchChain : target.searchChain()) {
            if (searchChain.providerId().equals(providerName))
                return searchChain;
        }
        fail("No provider " + providerName);
        return null;
    }

    private void assertUseByDefault(FederationConfig config, String chainName, boolean expectedValue) {
        FederationConfig.Target target = getTarget(config.target(), chainName);
        assertEquals(1, target.searchChain().size());
        assertEquals(expectedValue, target.searchChain().get(0).useByDefault());
    }

    private FederationConfig.Target getTarget(List<FederationConfig.Target> targets, String chainId) {
        for (FederationConfig.Target target : targets) {
            if (target.id().equals(chainId))
                return target;
        }
        fail("No target with id " + chainId);
        return null;
    }


}
