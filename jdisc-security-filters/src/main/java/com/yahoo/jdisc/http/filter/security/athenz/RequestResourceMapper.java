// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.yahoo.vespa.athenz.api.AthenzResourceName;

import java.util.Optional;

/**
 * Maps a request to an {@link AthenzResourceName} and an action.
 *
 * @author bjorncs
 */
public interface RequestResourceMapper {

    /**
     * @return A resource name + action to use for access control, empty if no access control should be performed.
     */
    Optional<ResourceNameAndAction> getResourceNameAndMapping(String method, String uriPath, String uriQuery);

    class ResourceNameAndAction {
        private final AthenzResourceName resourceName;
        private final String action;

        public ResourceNameAndAction(AthenzResourceName resourceName, String action) {
            this.resourceName = resourceName;
            this.action = action;
        }

        public AthenzResourceName resourceName() {
            return resourceName;
        }

        public String action() {
            return action;
        }
    }
}
