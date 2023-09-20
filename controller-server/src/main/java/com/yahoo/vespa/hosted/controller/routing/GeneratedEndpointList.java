// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.config.provision.zone.AuthMethod;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.GeneratedEndpoint;

import java.util.Collection;
import java.util.List;

/**
 * An immutable, filterable list of {@link GeneratedEndpoint}.
 *
 * @author mpolden
 */
public class GeneratedEndpointList extends AbstractFilteringList<GeneratedEndpoint, GeneratedEndpointList> {

    public static final GeneratedEndpointList EMPTY = new GeneratedEndpointList(List.of(), false);

    private GeneratedEndpointList(Collection<? extends GeneratedEndpoint> items, boolean negate) {
        super(items, negate, GeneratedEndpointList::new);
    }

    /** Returns the subset of endpoints which are generated for given endpoint ID */
    public GeneratedEndpointList declared(EndpointId endpoint) {
        return matching(e -> e.endpoint().isPresent() && e.endpoint().get().equals(endpoint));
    }

    /** Returns the subset of endpoints which are generated for endpoints declared in {@link com.yahoo.config.application.api.DeploymentSpec} */
    public GeneratedEndpointList declared() {
        return matching(GeneratedEndpoint::declared);
    }

    /** Returns the subset endpoints which are generated for clusters declared in {@link com.yahoo.vespa.hosted.controller.application.pkg.BasicServicesXml} */
    public GeneratedEndpointList cluster() {
        return not().declared();
    }

    /** Returns the subset of endpoints matching given auth method */
    public GeneratedEndpointList authMethod(AuthMethod authMethod) {
        return matching(ge -> ge.authMethod() == authMethod);
    }

    public static GeneratedEndpointList of(GeneratedEndpoint... generatedEndpoint) {
        return copyOf(List.of(generatedEndpoint));
    }

    public static GeneratedEndpointList copyOf(Collection<GeneratedEndpoint> generatedEndpoints) {
        return generatedEndpoints.isEmpty() ? EMPTY : new GeneratedEndpointList(generatedEndpoints, false);
    }

    @Override
    public String toString() {
        return asList().toString();
    }

}
