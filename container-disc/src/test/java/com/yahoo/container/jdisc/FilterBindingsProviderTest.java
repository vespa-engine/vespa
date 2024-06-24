// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.ChainedComponent;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.ChainsConfig.Chains;
import com.yahoo.container.core.ChainsConfig.Components;
import com.yahoo.container.core.ChainsConfig.Components.Builder;
import com.yahoo.container.http.filter.FilterChainRepository;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.filter.SecurityRequestFilterChain;
import com.yahoo.jdisc.http.server.jetty.FilterBindings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
public class FilterBindingsProviderTest {
    final ServerConfig.Builder configBuilder = new ServerConfig.Builder();

    @Test
    void requireThatEmptyInputGivesEmptyOutput() {
        final FilterChainRepository filterChainRepository = new FilterChainRepository(
                new ChainsConfig(new ChainsConfig.Builder()),
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new ComponentRegistry<>());

        final FilterBindingsProvider provider = new FilterBindingsProvider(
                new ComponentId("foo"),
                new ServerConfig(configBuilder),
                filterChainRepository,
                new ComponentRegistry<>());

        final FilterBindings filterBindings = provider.get();

        assertNotNull(filterBindings);
        assertTrue(filterBindings.requestFilterIds().isEmpty());
        assertTrue(filterBindings.responseFilterIds().isEmpty());
    }

    @Test
    void requireThatChainOrderIsCorrect() {

        List<ChainedComponent> filteredBy = new ArrayList<>();

        class DummyFilter extends ChainedComponent implements SecurityRequestFilter {
            @Override public void filter(DiscFilterRequest request, ResponseHandler handler) { filteredBy.add(this); }
            @Override public String toString() { return getClass().getAnnotation(Provides.class).value()[0]; }
        }

        @Provides("foo") class Filter1 extends DummyFilter { }
        @Provides("bar") @After("foo") class Filter2 extends DummyFilter { }
        @Provides("baz") @Before("foo") class Filter3 extends DummyFilter { }

        ComponentRegistry<SecurityRequestFilter> filters = new ComponentRegistry<>();

        SecurityRequestFilter foo = new Filter1();
        SecurityRequestFilter bar = new Filter2();
        SecurityRequestFilter baz = new Filter3();

        filters.register(ComponentId.fromString("foo"), foo);
        filters.register(ComponentId.fromString("bar"), bar);
        filters.register(ComponentId.fromString("baz"), baz);

        ChainsConfig.Builder oneChain = new ChainsConfig.Builder();
        oneChain.components(new Components.Builder().id("foo"))
                .components(new Components.Builder().id("bar"))
                .components(new Components.Builder().id("baz"))
                .chains(new Chains.Builder().id("chain")
                                           .components("bar")
                                           .components("foo")
                                           .components("baz"));

        ((SecurityRequestFilterChain) new FilterChainRepository(oneChain.build(),
                                                                new ComponentRegistry<>(),
                                                                new ComponentRegistry<>(),
                                                                filters,
                                                                new ComponentRegistry<>())
                .getFilter(ComponentSpecification.fromString("chain"))).filter((DiscFilterRequest) null, null);

        assertEquals(List.of(baz, foo, bar), filteredBy);

        filteredBy.clear();
        ChainsConfig.Builder childChain = new ChainsConfig.Builder();
        childChain.components(new Builder().id("foo"))
                  .components(new Builder().id("bar"))
                  .components(new Builder().id("baz"))
                  .chains(new Chains.Builder().id("parent")
                                              .components("bar")
                                              .components("baz"))
                  .chains(new Chains.Builder().id("child")
                                              .components("foo")
                                              .inherits("parent"));

        ((SecurityRequestFilterChain) new FilterChainRepository(childChain.build(),
                                                                new ComponentRegistry<>(),
                                                                new ComponentRegistry<>(),
                                                                filters,
                                                                new ComponentRegistry<>())
                .getFilter(ComponentSpecification.fromString("child"))).filter((DiscFilterRequest) null, null);

        assertEquals(List.of(baz, foo, bar), filteredBy);

    }

    @Test
    void requireThatCorrectlyConfiguredFiltersAreIncluded() {
        final String requestFilter1Id = "requestFilter1";
        final String requestFilter2Id = "requestFilter2";
        final String requestFilter3Id = "requestFilter3";
        final String responseFilter1Id = "responseFilter1";
        final String responseFilter2Id = "responseFilter2";
        final String responseFilter3Id = "responseFilter3";

        // Set up config.
        configBuilder.filter(new ServerConfig.Filter.Builder().id(requestFilter1Id).binding("http://*/a"));
        configBuilder.filter(new ServerConfig.Filter.Builder().id(requestFilter2Id).binding("http://*/b"));
        configBuilder.filter(new ServerConfig.Filter.Builder().id(responseFilter1Id).binding("http://*/c"));
        configBuilder.filter(new ServerConfig.Filter.Builder().id(responseFilter3Id).binding("http://*/d"));

        // Set up registry.
        final ComponentRegistry<RequestFilter> availableRequestFilters = new ComponentRegistry<>();
        final RequestFilter requestFilter1Instance = mock(RequestFilter.class);
        final RequestFilter requestFilter2Instance = mock(RequestFilter.class);
        final RequestFilter requestFilter3Instance = mock(RequestFilter.class);
        availableRequestFilters.register(ComponentId.fromString(requestFilter1Id), requestFilter1Instance);
        availableRequestFilters.register(ComponentId.fromString(requestFilter2Id), requestFilter2Instance);
        availableRequestFilters.register(ComponentId.fromString(requestFilter3Id), requestFilter3Instance);
        final ComponentRegistry<ResponseFilter> availableResponseFilters = new ComponentRegistry<>();
        final ResponseFilter responseFilter1Instance = mock(ResponseFilter.class);
        final ResponseFilter responseFilter2Instance = mock(ResponseFilter.class);
        final ResponseFilter responseFilter3Instance = mock(ResponseFilter.class);
        availableResponseFilters.register(ComponentId.fromString(responseFilter1Id), responseFilter1Instance);
        availableResponseFilters.register(ComponentId.fromString(responseFilter2Id), responseFilter2Instance);
        availableResponseFilters.register(ComponentId.fromString(responseFilter3Id), responseFilter3Instance);
        final FilterChainRepository filterChainRepository = new FilterChainRepository(
                new ChainsConfig(new ChainsConfig.Builder()),
                availableRequestFilters,
                availableResponseFilters,
                new ComponentRegistry<>(),
                new ComponentRegistry<>());

        // Set up the provider that we aim to test.
        final FilterBindingsProvider provider = new FilterBindingsProvider(
                new ComponentId("foo"),
                new ServerConfig(configBuilder),
                filterChainRepository,
                new ComponentRegistry<>());

        // Execute.
        final FilterBindings filterBindings = provider.get();

        // Verify.
        assertNotNull(filterBindings);
        assertEquals(filterBindings.requestFilters().stream().collect(Collectors.toSet()),
                Set.of(requestFilter1Instance, requestFilter2Instance));
        assertEquals(filterBindings.responseFilters().stream().collect(Collectors.toSet()),
                Set.of(responseFilter1Instance, responseFilter3Instance));
    }

    private interface DualRoleFilter extends RequestFilter, ResponseFilter {}

    @Test
    void requireThatInstanceCanNotBeBothRequestAndResponseFilter() {
        final String filterId = "filter";

        // Set up config.
        configBuilder.filter(new ServerConfig.Filter.Builder().id(filterId).binding("http://*/*"));

        // Set up registry.
        final DualRoleFilter filterInstance = mock(DualRoleFilter.class);
        final ComponentRegistry<RequestFilter> availableRequestFilters = new ComponentRegistry<>();
        availableRequestFilters.register(ComponentId.fromString(filterId), filterInstance);
        final FilterChainRepository filterChainRepository = new FilterChainRepository(
                new ChainsConfig(new ChainsConfig.Builder()),
                availableRequestFilters,
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new ComponentRegistry<>());

        try {
            new FilterBindingsProvider(
                    new ComponentId("foo"),
                    new ServerConfig(configBuilder),
                    filterChainRepository,
                    new ComponentRegistry<>());
            fail("Dual-role filter should not be accepted");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Invalid config"));
        }
    }

    @Test
    void requireThatConfigWithUnknownReferenceFails() {
        // Set up config.
        configBuilder.filter(new ServerConfig.Filter.Builder().id("someFilter").binding("http://*/*"));

        // Set up registry.
        final FilterChainRepository filterChainRepository = new FilterChainRepository(
                new ChainsConfig(new ChainsConfig.Builder()),
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new ComponentRegistry<>());

        try {
            new FilterBindingsProvider(
                    new ComponentId("foo"),
                    new ServerConfig(configBuilder),
                    filterChainRepository,
                    new ComponentRegistry<>());
            fail("Config with unknown filter reference should not be accepted");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Invalid config"));
        }
    }

}
