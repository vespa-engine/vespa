// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.mock;

import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzIdentity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author bjorncs
 */
public class AthenzDbMock {

    public final Map<AthenzDomain, Domain> domains = new HashMap<>();

    public AthenzDbMock addDomain(Domain domain) {
        domains.put(domain.name, domain);
        return this;
    }

    public static class Domain {

        public final AthenzDomain name;
        public final Set<AthenzIdentity> admins = new HashSet<>();
        public final Set<AthenzIdentity> tenantAdmins = new HashSet<>();
        public final Map<ApplicationId, Application> applications = new HashMap<>();
        public boolean isVespaTenant = false;

        public Domain(AthenzDomain name) {
            this.name = name;
        }

        public Domain admin(AthenzIdentity identity) {
            admins.add(identity);
            return this;
        }

        public Domain tenantAdmin(AthenzIdentity identity) {
            tenantAdmins.add(identity);
            return this;
        }

        /**
         * Simulates establishing Vespa tenancy in Athens.
         */
        public void markAsVespaTenant() {
            isVespaTenant = true;
        }

    }

    public static class Application {

        public final Map<ApplicationAction, Set<AthenzIdentity>> acl = new HashMap<>();

        public Application() {
            acl.put(ApplicationAction.deploy, new HashSet<>());
            acl.put(ApplicationAction.read, new HashSet<>());
            acl.put(ApplicationAction.write, new HashSet<>());
        }

        public Application addRoleMember(ApplicationAction action, AthenzIdentity identity) {
            acl.get(action).add(identity);
            return this;
        }
    }

}
