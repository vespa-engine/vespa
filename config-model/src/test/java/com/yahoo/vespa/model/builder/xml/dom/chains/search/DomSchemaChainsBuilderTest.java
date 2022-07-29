// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import com.yahoo.vespa.model.container.search.searchchain.GenericTarget;
import com.yahoo.vespa.model.container.search.searchchain.Provider;
import com.yahoo.vespa.model.container.search.searchchain.SearchChain;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.container.search.searchchain.Source;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.yahoo.container.core.ChainsConfig.Chains;
import static com.yahoo.container.core.ChainsConfig.Components;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test of Search chains builder.
 *
 * @author Tony Vaagenes
 */
public class DomSchemaChainsBuilderTest extends DomBuilderTest {

    private SearchChains searchChains;

    private static final Element element = parse(
            "<search>",
            "  <searcher id='searcher:1'/>",

            "  <provider id='provider:1' inherits='parentChain1 parentChain2' excludes='ExcludedSearcher1 ExcludedSearcher2'",
            "             cacheweight='2.3'>",
            "    <federationoptions optional='true' timeout='2.3 s' />",
            "    <nodes>",
            "      <node host='sourcehost' port='12'/>",
            "    </nodes>",

            "    <source id='source:1' inherits='parentChain3 parentChain4' excludes='ExcludedSearcher3 ExcludedSearcher4'>",
            "      <federationoptions timeout='12 ms' />",
            "    </source>",

            "  </provider>",

            "  <chain id='default'>",
            "    <federation id='federationSearcher'>",
            "      <source id='mysource'>",
            "        <federationoptions optional='false' />",
            "      </source>",
            "    </federation>",
            "  </chain>",

            "</search>");


    @BeforeEach
    public void createSearchChains() {
        searchChains = new DomSearchChainsBuilder().build(root.getDeployState(), root, element);
    }

    @Test
    void referToFederationAsSearcher() {
        final Element element = parse(
                "<search>",
                "  <federation id='federationSearcher'>",
                "    <source id='mysource'>",
                "      <federationoptions optional='false' />",
                "    </source>",
                "  </federation>",

                "  <chain id='default'>",
                "    <searcher id='federationSearcher'/>",
                "  </chain>",
                "</search>");

        try {
            MockRoot root = new MockRoot();
            new DomSearchChainsBuilder().build(root.getDeployState(), root, element);
            fail("Expected exception when referring to an outer 'federation' as a 'searcher'.");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Two different types declared for the component with name 'federationSearcher'"));
        }
    }

    @Test
    void ensureSearchChainsExists() {
        for (String id : Arrays.asList("provider:1", "source:1@provider:1", "default")) {
            assertNotNull(getSearchChain(id), "Missing search chain " + id);
        }
    }

    @Test
    void ensureSearcherExists() {
        assertThat(searchChains.allComponents(), hasItem(searcherWithId("searcher:1")));
    }

    private Matcher<ChainedComponent<?>> searcherWithId(final String componentId) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object o) {
                return o instanceof ChainedComponent &&
                        ((ChainedComponent) o).getComponentId().equals(new ComponentId(componentId));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a searcher with id ").appendValue(componentId);
            }
        };
    }

    @Test
    void checkProviderFederationOptions() {
        FederationOptions options = getProvider().federationOptions();

        assertTrue(options.getOptional());
        assertEquals(2300, options.getTimeoutInMilliseconds());
    }

    @Test
    void checkSourceFederationOptions() {
        FederationOptions options = getSource().federationOptions();

        assertTrue(options.getOptional()); //inherited
        assertEquals(12, options.getTimeoutInMilliseconds());
    }

    @Test
    void checkDefaultTargets() {
        Collection<? extends GenericTarget> defaultTargets =
                getProvider().defaultFederationTargets();

        assertEquals(1, defaultTargets.size());
        assertEquals(getSearchChain("source:1@provider:1"), first(defaultTargets));
    }

    @Test
    void checkInnerSearcherIdIsNestedInSearchChainId() {
        ChainsConfig.Builder builder = new ChainsConfig.Builder();
        searchChains.getConfig(builder);
        ChainsConfig config = new ChainsConfig(builder);

        checkInnerSearcherIdIsNestedInSearchChainId(config, "federationSearcher", "default");
    }

    private void checkInnerSearcherIdIsNestedInSearchChainId(ChainsConfig config,
                                                             String partOfSearcherName,
                                                             String searchChainName) {
        Components searcher = getSearcherConfig(config.components(), partOfSearcherName);
        ComponentId searcherId = ComponentId.fromString(searcher.id());

        assertEquals(getSearchChain(searchChainName).getComponentId(), searcherId.getNamespace());

        Chains searchChain = getSearchChainConfig(config.chains(), searchChainName);
        assertEquals(searcherId, ComponentId.fromString(searchChain.components(0)));
    }

    private Chains getSearchChainConfig(List<Chains> searchChains,
                                                                String searchChainName) {
        for (Chains searchChain : searchChains) {
            if (ComponentId.fromString(searchChain.id()).getName().equals(searchChainName))
                return searchChain;
        }
        fail("No search chain matching " + searchChainName);
        return null;
    }

    private Components getSearcherConfig(List<Components> searchers, String partOfId) {
        for (Components searcher : searchers) {
            if (searcher.id().contains(partOfId))
                return searcher;
        }
        fail("No searcher matching " + partOfId);
        return null;
    }

    private static <T> T first(Iterable<T> coll) {
        return coll.iterator().next();
    }

    private Provider getProvider() {
        return (Provider)getSearchChain("provider:1");
    }

    private Source getSource() {
        return first(getProvider().getSources());
    }

    private SearchChain getSearchChain(String componentSpecification) {
        return searchChains.allChains().getComponent(new ComponentSpecification(componentSpecification));
    }

}
