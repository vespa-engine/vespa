// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.http.filter.FilterChainRepository;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.filter.SecurityRequestFilterChain;
import com.yahoo.jdisc.http.server.jetty.FilterBindings;

import java.util.List;

/**
 * Helper class to set up filter binding repositories based on config.
 *
 * @author Øyvind Bakksjø
 * @author bjorncs
 */
class FilterUtil {

    private static final ComponentId SEARCH_SERVER_COMPONENT_ID = ComponentId.fromString("SearchServer");

    private final FilterBindings.Builder builder = new FilterBindings.Builder();

    private FilterUtil() {}

    private void configureFilters(List<FilterSpec> filtersConfig, FilterChainRepository filterChainRepository) {
        for (FilterSpec filterConfig : filtersConfig) {
            Object filter = filterChainRepository.getFilter(ComponentSpecification.fromString(filterConfig.getId()));
            if (filter == null) {
                throw new RuntimeException("No http filter with id " + filterConfig.getId());
            }
            addFilter(filter, filterConfig.getBinding(), filterConfig.getId());
        }
    }

    private void addFilter(Object filter, String binding, String filterId) {
        if (filter instanceof RequestFilter && filter instanceof ResponseFilter) {
            throw new RuntimeException("The filter " + filter.getClass().getName() + 
                                       " is unsupported since it's both a RequestFilter and a ResponseFilter.");
        } else if (filter instanceof RequestFilter) {
            builder.addRequestFilter(filterId, binding, (RequestFilter) filter);
        } else if (filter instanceof ResponseFilter) {
            builder.addResponseFilter(filterId, binding, (ResponseFilter) filter);
        } else {
            throw new RuntimeException("Unknown filter type " + filter.getClass().getName());
        }
    }

    // TODO(gjoranv): remove
    private void configureLegacyFilters(ComponentId id, ComponentRegistry<SecurityRequestFilter> legacyRequestFilters) {
        ComponentId serverName = id.getNamespace();
        if (SEARCH_SERVER_COMPONENT_ID.equals(serverName) && !legacyRequestFilters.allComponents().isEmpty()) {
            builder.addRequestFilter(
                    "legacy-filters", "http://*/*", SecurityRequestFilterChain.newInstance(legacyRequestFilters.allComponents()));
        }
    }

    /**
     * Populates binding repositories with filters based on config.
     */
    public static FilterBindings setupFilters(
            ComponentId componentId,
            ComponentRegistry<SecurityRequestFilter> legacyRequestFilters,
            List<FilterSpec> filtersConfig,
            FilterChainRepository filterChainRepository) {
        FilterUtil filterUtil = new FilterUtil();

        // TODO(gjoranv): remove
        filterUtil.configureLegacyFilters(componentId, legacyRequestFilters);

        filterUtil.configureFilters(filtersConfig, filterChainRepository);

        return filterUtil.builder.build();
    }

    public static class FilterSpec {

        private final String id;
        private final String binding;

        public FilterSpec(String id, String binding) {
            this.id = id;
            this.binding = binding;
        }

        public String getId() {
            return id;
        }

        public String getBinding() {
            return binding;
        }
    }

}
