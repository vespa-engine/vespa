// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.yahoo.jdisc.http.filter.DiscFilterRequest;
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
    Optional<ResourceNameAndAction> getResourceNameAndAction(String method, String uriPath, String uriQuery);

    /**
     * @return A resource name + action to use for access control, empty if no access control should be performed.
     */
    default Optional<ResourceNameAndAction> getResourceNameAndAction(DiscFilterRequest request) {
        return getResourceNameAndAction(request.getMethod(), request.getRequestURI(), request.getQueryString());
    }

    class ResourceNameAndAction {
        private final AthenzResourceName resourceName;
        private final String action;
        private final String futureAction;

        public ResourceNameAndAction(AthenzResourceName resourceName, String action) {
            this(resourceName, action, action);
        }
        public ResourceNameAndAction(AthenzResourceName resourceName, String action, String futureAction) {
            this.resourceName = resourceName;
            this.action = action;
            this.futureAction = futureAction;
        }

        public AthenzResourceName resourceName() {
            return resourceName;
        }

        public String action() {
            return action;
        }

        public ResourceNameAndAction withFutureAction(String futureAction) {
            return new ResourceNameAndAction(resourceName, action, futureAction);
        }

        public String futureAction() {
            return futureAction;
        }

        @Override
        public String toString() {
            return "ResourceNameAndAction{" +
                    "resourceName=" + resourceName +
                    ", action='" + action + '\'' +
                    '}';
        }
    }
}
