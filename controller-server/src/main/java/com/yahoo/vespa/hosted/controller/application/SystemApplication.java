// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.text.Text;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * This represents a system-level application in hosted Vespa. All infrastructure nodes in a hosted Vespa zones are
 * allocated to a system application.
 *
 * @author mpolden
 */
public enum SystemApplication {

    controllerHost(InfrastructureApplication.CONTROLLER_HOST),
    configServerHost(InfrastructureApplication.CONFIG_SERVER_HOST),
    configServer(InfrastructureApplication.CONFIG_SERVER),
    proxyHost(InfrastructureApplication.PROXY_HOST),
    proxy(InfrastructureApplication.PROXY, proxyHost, configServer),
    tenantHost(InfrastructureApplication.TENANT_HOST);

    /** The tenant owning all system applications */
    public static final TenantName TENANT = TenantName.from(Constants.TENANT_NAME);

    private final InfrastructureApplication application;
    private final List<SystemApplication> dependencies;

    SystemApplication(InfrastructureApplication application, SystemApplication... dependencies) {
        this.application = application;
        this.dependencies = List.of(dependencies);
    }

    public ApplicationId id() {
        return application.id();
    }

    /** The node type that is implicitly allocated to this */
    public NodeType nodeType() {
        return application.nodeType();
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
    public boolean shouldUpgradeOs() {
        return nodeType().isHost();
    }

    /** All system applications that are not the controller */
    public static List<SystemApplication> notController() {
        return List.copyOf(EnumSet.complementOf(EnumSet.of(SystemApplication.controllerHost)));
    }

    /** All system applications */
    public static List<SystemApplication> all() {
        return List.of(values());
    }

    /** Returns the system application matching given id, if any */
    public static Optional<SystemApplication> matching(ApplicationId id) {
        return Arrays.stream(values()).filter(app -> app.id().equals(id)).findFirst();
    }

    @Override
    public String toString() {
        return Text.format("system application %s of type %s", id(), nodeType());
    }

    private static class Constants {
        private static final String TENANT_NAME = "hosted-vespa";
    }

}
