// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A set of {@link Endpoint} instances. Construct using {@link Cluster.Builder}.
 *
 * @author Einar M R Rosenvinge
 */
public final class Cluster {

    /** Builder for {@link Cluster}. */
    public final static class Builder {
        private final List<Endpoint> endpoints = new LinkedList<>();
        private String route = null;

        /**
         * Adds an Endpoint (a HTTP gateway) to this Cluster.
         *
         * @param endpoint the Endpoint to add
         * @return this, for chaining
         */
        public Builder addEndpoint(Endpoint endpoint) {
            endpoints.add(endpoint);
            return this;
        }

        /**
         * Sets a route specific to this cluster, which overrides the route set in {@link com.yahoo.vespa.http.client.config.FeedParams#getRoute()}.
         *
         * @param route a route specific to this cluster
         * @return this, for chaining
         */
        public Builder setRoute(String route) {
            this.route = route;
            return this;
        }

        public Cluster build() {
            return new Cluster(endpoints, route);
        }

        public String getRoute() {
            return route;
        }
    }
    private final List<Endpoint> endpoints;
    private final String route;

    private Cluster(List<Endpoint> endpoints, String route) {
        this.endpoints = Collections.unmodifiableList(new ArrayList<>(endpoints));
        this.route = route;
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public String getRoute() {
        return route;
    }

    @Override
    public String toString() {
        return "cluster with endpoints " + endpoints + " and route '" + route + "'";
    }

}
