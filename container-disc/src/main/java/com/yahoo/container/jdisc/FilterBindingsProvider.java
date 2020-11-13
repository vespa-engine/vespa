// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.http.filter.FilterChainRepository;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.server.jetty.FilterBindings;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides filter bindings based on vespa config.
 *
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
public class FilterBindingsProvider implements Provider<FilterBindings> {

    private final FilterBindings filterBindings;

    public FilterBindingsProvider(ComponentId componentId,
                                  ServerConfig config,
                                  FilterChainRepository filterChainRepository,
                                  ComponentRegistry<SecurityRequestFilter> legacyRequestFilters) {
        try {
            this.filterBindings = FilterUtil.setupFilters(
                    componentId,
                    legacyRequestFilters,
                    toFilterSpecs(config.filter()),
                    filterChainRepository);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Invalid config for http server '" + componentId.getNamespace() + "': " + e.getMessage(), e);
        }
    }

    private List<FilterUtil.FilterSpec> toFilterSpecs(List<ServerConfig.Filter> inFilters) {
        List<FilterUtil.FilterSpec> outFilters = new ArrayList<>();
        for (ServerConfig.Filter inFilter : inFilters) {
            outFilters.add(new FilterUtil.FilterSpec(inFilter.id(), inFilter.binding()));
        }
        return outFilters;
    }

    @Override public FilterBindings get() { return filterBindings; }

    @Override
    public void deconstruct() {}

}
