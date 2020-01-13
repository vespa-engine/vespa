// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;

import java.util.List;
import java.util.Optional;

/**
 * This represents a system-level application in hosted Vespa. All infrastructure nodes in a hosted Vespa zones are
 * allocated to a system application.
 *
 * @author mpolden
 */
public enum SystemApplication {

    configServerHost(ApplicationId.from("hosted-vespa", "configserver-host", "default"), NodeType.confighost),
    configServer(ApplicationId.from("hosted-vespa", "zone-config-servers", "default"), NodeType.config),
    proxyHost(ApplicationId.from("hosted-vespa", "proxy-host", "default"), NodeType.proxyhost),
    proxy(ApplicationId.from("hosted-vespa", "routing", "default"), NodeType.proxy, proxyHost, configServer),
    tenantHost(ApplicationId.from("hosted-vespa", "tenant-host", "default"), NodeType.host);

    private final ApplicationId id;
    private final NodeType nodeType;
    private final List<SystemApplication> dependencies;

    SystemApplication(ApplicationId id, NodeType nodeType, SystemApplication... dependencies) {
        this.id = id;
        this.nodeType = nodeType;
        this.dependencies = List.of(dependencies);
    }

    public ApplicationId id() {
        return id;
    }

    /** The node type that is implicitly allocated to this */
    public NodeType nodeType() {
        return nodeType;
    }

    /** Returns the system applications that should upgrade before this */
    public List<SystemApplication> dependencies() { return dependencies; }

    /** Returns whether this system application has an application package */
    public boolean hasApplicationPackage() {
        return this == proxy;
    }

    /** Returns whether config for this application has converged in given zone */
    public boolean configConvergedIn(ZoneId zone, Controller controller, Optional<Version> version) {
        if (!hasApplicationPackage()) {
            return true;
        }
        return controller.serviceRegistry().configServer().serviceConvergence(new DeploymentId(id(), zone), version)
                         .map(ServiceConvergence::converged)
                         .orElse(false);
    }

    /** Returns whether this should receive OS upgrades */
    public boolean isEligibleForOsUpgrades() {
        return nodeType.isDockerHost();
    }

    /** All known system applications */
    public static List<SystemApplication> all() {
        return List.of(values());
    }

    @Override
    public String toString() {
        return String.format("system application %s of type %s", id, nodeType);
    }

}
