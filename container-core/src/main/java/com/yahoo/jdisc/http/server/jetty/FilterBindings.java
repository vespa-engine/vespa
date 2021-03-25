// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Resolves request/response filter (chain) from a {@link URI} instance.
 *
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
public class FilterBindings {

    private final Map<String, RequestFilter> requestFilters;
    private final Map<String, ResponseFilter> responseFilters;
    private final Map<Integer, String> defaultRequestFilters;
    private final Map<Integer, String> defaultResponseFilters;
    private final BindingSet<String> requestFilterBindings;
    private final BindingSet<String> responseFilterBindings;

    private FilterBindings(
            Map<String, RequestFilter> requestFilters,
            Map<String, ResponseFilter> responseFilters,
            Map<Integer, String> defaultRequestFilters,
            Map<Integer, String> defaultResponseFilters,
            BindingSet<String> requestFilterBindings,
            BindingSet<String> responseFilterBindings) {
        this.requestFilters = requestFilters;
        this.responseFilters = responseFilters;
        this.defaultRequestFilters = defaultRequestFilters;
        this.defaultResponseFilters = defaultResponseFilters;
        this.requestFilterBindings = requestFilterBindings;
        this.responseFilterBindings = responseFilterBindings;
    }

    public Optional<String> resolveRequestFilter(URI uri, int localPort) {
        String filterId = requestFilterBindings.resolve(uri);
        if (filterId != null) return Optional.of(filterId);
        return Optional.ofNullable(defaultRequestFilters.get(localPort));
    }

    public Optional<String> resolveResponseFilter(URI uri, int localPort) {
        String filterId = responseFilterBindings.resolve(uri);
        if (filterId != null) return Optional.of(filterId);
        return Optional.ofNullable(defaultResponseFilters.get(localPort));
    }

    public RequestFilter getRequestFilter(String filterId) { return requestFilters.get(filterId); }

    public ResponseFilter getResponseFilter(String filterId) { return responseFilters.get(filterId); }

    public Collection<String> requestFilterIds() { return requestFilters.keySet(); }

    public Collection<String> responseFilterIds() { return responseFilters.keySet(); }

    public Collection<RequestFilter> requestFilters() { return requestFilters.values(); }

    public Collection<ResponseFilter> responseFilters() { return responseFilters.values(); }

    public static class Builder {
        private final Map<String, RequestFilter> requestFilters = new TreeMap<>();
        private final Map<String, ResponseFilter> responseFilters = new TreeMap<>();
        private final Map<Integer, String> defaultRequestFilters = new TreeMap<>();
        private final Map<Integer, String> defaultResponseFilters = new TreeMap<>();
        private final BindingRepository<String> requestFilterBindings = new BindingRepository<>();
        private final BindingRepository<String> responseFilterBindings = new BindingRepository<>();

        public Builder() {}

        public Builder addRequestFilter(String id, RequestFilter filter) { requestFilters.put(id, filter); return this; }

        public Builder addResponseFilter(String id, ResponseFilter filter) { responseFilters.put(id, filter); return this; }

        public Builder addRequestFilterBinding(String id, String binding) { requestFilterBindings.bind(binding, id); return this; }

        public Builder addResponseFilterBinding(String id, String binding) { responseFilterBindings.bind(binding, id); return this; }

        public Builder setRequestFilterDefaultForPort(String id, int port) { defaultRequestFilters.put(port, id); return this; }

        public Builder setResponseFilterDefaultForPort(String id, int port) { defaultResponseFilters.put(port, id); return this; }

        public FilterBindings build() {
            return new FilterBindings(
                    Collections.unmodifiableMap(requestFilters),
                    Collections.unmodifiableMap(responseFilters),
                    Collections.unmodifiableMap(defaultRequestFilters),
                    Collections.unmodifiableMap(defaultResponseFilters),
                    requestFilterBindings.activate(),
                    responseFilterBindings.activate());
        }
    }
}
