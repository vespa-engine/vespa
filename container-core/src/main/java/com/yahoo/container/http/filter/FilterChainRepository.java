// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.http.filter;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.ChainedComponent;
import com.yahoo.component.chain.ChainsConfigurer;
import com.yahoo.component.chain.model.ChainsModel;
import com.yahoo.component.chain.model.ChainsModelBuilder;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.filter.SecurityRequestFilterChain;
import com.yahoo.jdisc.http.filter.SecurityResponseFilter;
import com.yahoo.jdisc.http.filter.SecurityResponseFilterChain;
import com.yahoo.jdisc.http.filter.chain.RequestFilterChain;
import com.yahoo.jdisc.http.filter.chain.ResponseFilterChain;
import com.yahoo.processing.execution.chain.ChainRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Creates JDisc request/response filter chains.
 *
 * @author Tony Vaagenes
 * @author bjorncs
 */
public class FilterChainRepository extends AbstractComponent {

    private final ComponentRegistry<Object> filterAndChains;

    public FilterChainRepository(ChainsConfig chainsConfig,
                                 ComponentRegistry<RequestFilter> requestFilters,
                                 ComponentRegistry<ResponseFilter> responseFilters,
                                 ComponentRegistry<SecurityRequestFilter> securityRequestFilters,
                                 ComponentRegistry<SecurityResponseFilter> securityResponseFilters) {
        ComponentRegistry<Object> filterAndChains = new ComponentRegistry<>();
        addAllFilters(filterAndChains, requestFilters, responseFilters, securityRequestFilters, securityResponseFilters);
        addAllChains(filterAndChains, chainsConfig, requestFilters, responseFilters, securityRequestFilters, securityResponseFilters);
        filterAndChains.freeze();
        this.filterAndChains = filterAndChains;
    }

    public Object getFilter(ComponentSpecification componentSpecification) {
        return filterAndChains.getComponent(componentSpecification);
    }

    private static void addAllFilters(ComponentRegistry<Object> destination,
                                      ComponentRegistry<?>... registries) {
        for (ComponentRegistry<?> registry : registries) {
            registry.allComponentsById()
                    .forEach((id, filter) -> destination.register(id, wrapIfSecurityFilter(filter)));
        }
    }

    private static void addAllChains(ComponentRegistry<Object> destination,
                                     ChainsConfig chainsConfig,
                                     ComponentRegistry<?>... filters) {
        ChainRegistry<FilterWrapper> chainRegistry = buildChainRegistry(chainsConfig, filters);
        chainRegistry.allComponents()
                .forEach(chain -> destination.register(chain.getId(), toJDiscChain(chain)));
    }

    private static ChainRegistry<FilterWrapper> buildChainRegistry(ChainsConfig chainsConfig,
                                                                   ComponentRegistry<?>... filters) {
        ChainRegistry<FilterWrapper> chainRegistry = new ChainRegistry<>();
        ChainsModel chainsModel = ChainsModelBuilder.buildFromConfig(chainsConfig);
        ChainsConfigurer.prepareChainRegistry(chainRegistry, chainsModel, allFiltersWrapped(filters));
        chainRegistry.freeze();
        return chainRegistry;
    }

    @SuppressWarnings("unchecked")
    private static Object toJDiscChain(Chain<FilterWrapper> chain) {
        checkFilterTypesCompatible(chain);
        List<?> jdiscFilters = chain.components().stream()
                        .map(filterWrapper -> filterWrapper.filter)
                        .collect(toList());
        List<?> wrappedFilters = wrapSecurityFilters(jdiscFilters);
        Object head = wrappedFilters.get(0);
        if (wrappedFilters.size() == 1) return head;
        else if (head instanceof RequestFilter)
            return RequestFilterChain.newInstance((List<RequestFilter>) wrappedFilters);
        else if (head instanceof ResponseFilter)
            return ResponseFilterChain.newInstance((List<ResponseFilter>) wrappedFilters);
        throw new IllegalStateException();
    }

    private static List<?> wrapSecurityFilters(List<?> filters) {
        if (filters.isEmpty()) return emptyList();
        List<Object> aggregatedSecurityFilters = new ArrayList<>();
        List<Object> wrappedFilters = new ArrayList<>();
        for (Object filter : filters) {
            if (isSecurityFilter(filter)) {
                aggregatedSecurityFilters.add(filter);
            } else {
                if (!aggregatedSecurityFilters.isEmpty()) {
                    wrappedFilters.add(createSecurityChain(aggregatedSecurityFilters));
                    aggregatedSecurityFilters.clear();
                }
                wrappedFilters.add(filter);
            }
        }
        if (!aggregatedSecurityFilters.isEmpty()) {
            wrappedFilters.add(createSecurityChain(aggregatedSecurityFilters));
        }
        return wrappedFilters;
    }

    private static void checkFilterTypesCompatible(Chain<FilterWrapper> chain) {
        Set<ComponentId> requestFilters = chain.components().stream()
                .filter(filter -> filter instanceof RequestFilter || filter instanceof SecurityRequestFilter)
                .map(FilterWrapper::getId)
                .collect(toSet());
        Set<ComponentId> responseFilters = chain.components().stream()
                .filter(filter -> filter instanceof ResponseFilter || filter instanceof SecurityResponseFilter)
                .map(FilterWrapper::getId)
                .collect(toSet());
        if (!requestFilters.isEmpty() && !responseFilters.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Can't mix request and response filters in chain %s: request filters: %s, response filters: %s.",
                            chain.getId(), requestFilters, responseFilters));
        }
    }

    private static ComponentRegistry<FilterWrapper> allFiltersWrapped(ComponentRegistry<?>... registries) {
        ComponentRegistry<FilterWrapper> wrappedFilters = new ComponentRegistry<>();
        for (ComponentRegistry<?> registry : registries) {
            registry.allComponentsById()
                    .forEach((id, filter) -> wrappedFilters.register(id, new FilterWrapper(id, filter)));
        }
        wrappedFilters.freeze();
        return wrappedFilters;
    }

    private static Object wrapIfSecurityFilter(Object filter) {
        if (isSecurityFilter(filter)) return createSecurityChain(Collections.singletonList(filter));
        return filter;
    }

    @SuppressWarnings("unchecked")
    private static Object createSecurityChain(List<?> filters) {
        Object head = filters.get(0);
        if (head instanceof SecurityRequestFilter)
            return SecurityRequestFilterChain.newInstance((List<SecurityRequestFilter>) filters);
        else if (head instanceof SecurityResponseFilter)
            return SecurityResponseFilterChain.newInstance((List<SecurityResponseFilter>) filters);
        throw new IllegalArgumentException("Unexpected class " + head.getClass());
    }

    private static boolean isSecurityFilter(Object filter) {
        return filter instanceof SecurityRequestFilter || filter instanceof SecurityResponseFilter;
    }

    private static class FilterWrapper extends ChainedComponent {
        public final Object filter;
        public final Class<?> filterType;

        public FilterWrapper(ComponentId id, Object filter) {
            super(id);
            this.filter = filter;
            this.filterType = getFilterType(filter);
        }

        private static Class<?> getFilterType(Object filter) {
            if (filter instanceof RequestFilter)
                return RequestFilter.class;
            else if (filter instanceof ResponseFilter)
                return ResponseFilter.class;
            else if (filter instanceof SecurityRequestFilter)
                return SecurityRequestFilter.class;
            else if (filter instanceof SecurityResponseFilter)
                return SecurityResponseFilter.class;
            throw new IllegalArgumentException("Unsupported filter type: " + filter.getClass().getName());
        }
    }
}
