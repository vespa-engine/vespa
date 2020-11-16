// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.http.filter.FilterChainRepository;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.filter.SecurityRequestFilterChain;
import com.yahoo.jdisc.http.server.jetty.FilterBindings;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides filter bindings based on vespa config.
 *
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
public class FilterBindingsProvider implements Provider<FilterBindings> {

    private static final ComponentId SEARCH_SERVER_COMPONENT_ID = ComponentId.fromString("SearchServer");

    private final FilterBindings filterBindings;

    @Inject
    public FilterBindingsProvider(ComponentId componentId,
                                  ServerConfig config,
                                  FilterChainRepository filterChainRepository,
                                  ComponentRegistry<SecurityRequestFilter> legacyRequestFilters) {
        try {
            FilterBindings.Builder builder = new FilterBindings.Builder();
            configureLegacyFilters(builder, componentId, legacyRequestFilters);
            configureFilters(builder, config, filterChainRepository);
            this.filterBindings = builder.build();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Invalid config for http server '" + componentId.getNamespace() + "': " + e.getMessage(), e);
        }
    }

    // TODO(gjoranv): remove
    private static void configureLegacyFilters(
            FilterBindings.Builder builder,
            ComponentId id,
            ComponentRegistry<SecurityRequestFilter> legacyRequestFilters) {
        ComponentId serverName = id.getNamespace();
        if (SEARCH_SERVER_COMPONENT_ID.equals(serverName) && !legacyRequestFilters.allComponents().isEmpty()) {
            String filterId = "legacy-filters";
            builder.addRequestFilter(filterId, SecurityRequestFilterChain.newInstance(legacyRequestFilters.allComponents()));
            builder.addRequestFilterBinding(filterId, "http://*/*");
        }
    }

    private static void configureFilters(
            FilterBindings.Builder builder, ServerConfig config, FilterChainRepository filterRepository) {
        addFilterInstances(builder, config, filterRepository);
        addFilterBindings(builder, config, filterRepository);
        addPortDefaultFilters(builder, config, filterRepository);
    }

    private static void addFilterInstances(
            FilterBindings.Builder builder, ServerConfig config, FilterChainRepository filterRepository) {
        Set<String> filterIds = new HashSet<>();
        config.filter().forEach(filterBinding -> filterIds.add(filterBinding.id()));
        config.defaultFilters().forEach(defaultFilter -> filterIds.add(defaultFilter.filterId()));
        for (String filterId : filterIds) {
            Object filterInstance = getFilterInstance(filterRepository, filterId);
            if (filterInstance instanceof RequestFilter && filterInstance instanceof ResponseFilter) {
                throw new IllegalArgumentException("The filter " + filterInstance.getClass().getName()
                        + " is unsupported since it's both a RequestFilter and a ResponseFilter.");
            } else if (filterInstance instanceof RequestFilter) {
                builder.addRequestFilter(filterId, (RequestFilter)filterInstance);
            } else if (filterInstance instanceof ResponseFilter) {
                builder.addResponseFilter(filterId, (ResponseFilter)filterInstance);
            } else if (filterInstance == null) {
                throw new IllegalArgumentException("No http filter with id " + filterId);
            } else {
                throw new IllegalArgumentException("Unknown filter type: " + filterInstance.getClass().getName());
            }
        }
    }

    private static void addFilterBindings(
            FilterBindings.Builder builder, ServerConfig config, FilterChainRepository filterRepository) {
        for (ServerConfig.Filter filterBinding : config.filter()) {
            if (isRequestFilter(filterRepository, filterBinding.id())) {
                builder.addRequestFilterBinding(filterBinding.id(), filterBinding.binding());
            } else {
                builder.addResponseFilterBinding(filterBinding.id(), filterBinding.binding());
            }
        }
    }

    private static void addPortDefaultFilters(
            FilterBindings.Builder builder, ServerConfig config, FilterChainRepository filterRepository) {
        for (ServerConfig.DefaultFilters defaultFilter : config.defaultFilters()) {
            if (isRequestFilter(filterRepository, defaultFilter.filterId())) {
                builder.setRequestFilterDefaultForPort(defaultFilter.filterId(), defaultFilter.localPort());
            } else {
                builder.setResponseFilterDefaultForPort(defaultFilter.filterId(), defaultFilter.localPort());
            }
        }
    }

    private static boolean isRequestFilter(FilterChainRepository filterRepository, String filterId) {
        return getFilterInstance(filterRepository, filterId) instanceof RequestFilter;
    }

    private static Object getFilterInstance(FilterChainRepository filterRepository, String filterId) {
        return filterRepository.getFilter(ComponentSpecification.fromString(filterId));
    }

    @Override public FilterBindings get() { return filterBindings; }

    @Override public void deconstruct() {}

}
