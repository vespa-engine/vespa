// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toSet;

/**
 * Resolves request/response filter (chain) from a {@link URI} instance.
 *
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
public class FilterBindings {

    private final BindingSet<FilterHolder<RequestFilter>> requestFilters;
    private final BindingSet<FilterHolder<ResponseFilter>> responseFilters;

    private FilterBindings(
            BindingSet<FilterHolder<RequestFilter>> requestFilters,
            BindingSet<FilterHolder<ResponseFilter>> responseFilters) {
        this.requestFilters = requestFilters;
        this.responseFilters = responseFilters;
    }

    public Optional<String> resolveRequestFilter(URI uri) { return resolveFilterId(requestFilters, uri); }

    public Optional<String> resolveResponseFilter(URI uri) { return resolveFilterId(responseFilters, uri); }

    public RequestFilter getRequestFilter(String filterId) { return getFilterInstance(requestFilters, filterId); }

    public ResponseFilter getResponseFilter(String filterId) { return getFilterInstance(responseFilters, filterId); }

    public Collection<String> requestFilterIds() { return filterIds(requestFilters); }

    public Collection<String> responseFilterIds() { return filterIds(responseFilters); }

    public Collection<RequestFilter> requestFilters() { return filters(requestFilters); }

    public Collection<ResponseFilter> responseFilters() { return filters(responseFilters); }

    private static <T> Optional<String> resolveFilterId(BindingSet<FilterHolder<T>> filters, URI uri) {
        return Optional.ofNullable(filters.resolve(uri))
                .map(holder -> holder.filterId);
    }

    private static <T> T getFilterInstance(BindingSet<FilterHolder<T>> filters, String filterId) {
        return stream(filters)
                .filter(filterEntry -> filterId.equals(filterEntry.getValue().filterId))
                .map(filterEntry -> filterEntry.getValue().filterInstance)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("No filter with id " + filterId));
    }

    private static <T> Collection<String> filterIds(BindingSet<FilterHolder<T>> filters) {
        return stream(filters)
                .map(filterEntry -> filterEntry.getValue().filterId)
                .collect(toSet());
    }

    private static <T> Collection<T> filters(BindingSet<FilterHolder<T>> filters) {
        return stream(filters)
                .map(filterEntry -> filterEntry.getValue().filterInstance)
                .collect(toSet());
    }

    private static <T> Stream<Map.Entry<UriPattern, FilterHolder<T>>> stream(BindingSet<FilterHolder<T>> filters) {
        return StreamSupport.stream(filters.spliterator(), false);
    }

    public static class Builder {
        private final BindingRepository<FilterHolder<RequestFilter>> requestFilters = new BindingRepository<>();
        private final BindingRepository<FilterHolder<ResponseFilter>> responseFilters = new BindingRepository<>();

        public Builder() {}

        public Builder addRequestFilter(String id, String binding, RequestFilter filter) {
            requestFilters.bind(binding, new FilterHolder<>(id, filter));
            return this;
        }

        public Builder addResponseFilter(String id, String binding, ResponseFilter filter) {
            responseFilters.bind(binding, new FilterHolder<>(id, filter));
            return this;
        }

        public FilterBindings build() { return new FilterBindings(requestFilters.activate(), responseFilters.activate()); }
    }

    private static class FilterHolder<T> {
        final String filterId;
        final T filterInstance;

        FilterHolder(String filterId, T filterInstance) {
            this.filterId = filterId;
            this.filterInstance = filterInstance;
        }
    }
}
