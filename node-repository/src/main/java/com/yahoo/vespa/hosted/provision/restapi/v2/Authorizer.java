// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * An authorizer for the node-repository REST API. This contains the authorization rules for all API paths in this
 * module.
 *
 * @author mpolden
 */
public class Authorizer implements BiPredicate<Principal, URI> {

    private final SystemName system;
    private final NodeRepository nodeRepository;

    public Authorizer(SystemName system, NodeRepository nodeRepository) {
        this.system = system;
        this.nodeRepository = nodeRepository;
    }

    /** Returns whether principal is authorized to access given URI */
    @Override
    public boolean test(Principal principal, URI uri) {
        // Trusted services can access everything
        if (principal.getName().equals(trustedService())) {
            return true;
        }

        // Nodes can only access its own resources
        if (isNodeResource(uri) && canAccess(hostnameFrom(uri), principal)) {
            return true;
        }

        // For resources that support filtering, nodes can only apply filter to themselves and their children
        if (supportsFiltering(uri) && canAccess(hostnamesFrom(uri), principal)) {
            return true;
        }

        return false;
    }

    /** Returns whether principal can access node identified by hostname */
    private boolean canAccess(String hostname, Principal principal) {
        // Node can always access itself
        if (principal.getName().equals(hostname)) {
            return true;
        }
        // Parent node can access its children
        return nodeRepository.getNode(hostname)
                             .flatMap(Node::parentHostname)
                             .map(parentHostname -> principal.getName().equals(parentHostname))
                             .orElse(false);
    }

    /** Returns whether principal can access all nodes identified by given hostnames */
    private boolean canAccess(List<String> hostnames, Principal principal) {
        return !hostnames.isEmpty() && hostnames.stream().allMatch(hostname -> canAccess(hostname, principal));
    }

    /** Trusted service name for this system */
    private String trustedService() {
        if (system != SystemName.main) {
            return "vespa.vespa." + system.name() + ".hosting";
        }
        return "vespa.vespa.hosting";
    }

    /** Returns the last element (basename) of given path */
    private static String hostnameFrom(URI uri) {
        return Paths.get(uri.getPath()).getFileName().toString();
    }

    /** Returns hostnames contained in query parameters of given URI */
    private static List<String> hostnamesFrom(URI uri) {
        return URLEncodedUtils.parse(uri, StandardCharsets.UTF_8.name())
                              .stream()
                              .filter(pair -> "hostname".equals(pair.getName()) ||
                                              "parentHost".equals(pair.getName()))
                              .map(NameValuePair::getValue)
                              .filter(hostname -> !hostname.isEmpty())
                              .collect(Collectors.toList());
    }

    /** Returns whether given URI is a node-specific resource, e.g. /nodes/v2/node/node1.fqdn */
    private static boolean isNodeResource(URI uri) {
        return isChildOf("/nodes/v2/acl/", uri.getPath()) ||
               isChildOf("/nodes/v2/node/", uri.getPath()) ||
               isChildOf("/nodes/v2/state/", uri.getPath());
    }

    /** Returns whether given path supports filtering through query parameters */
    private static boolean supportsFiltering(URI uri) {
        return isChildOf("/nodes/v2/command/", uri.getPath()) ||
               "/nodes/v2/node/".equals(uri.getPath());
    }

    /** Returns whether child is a sub-path of parent */
    private static boolean isChildOf(String parent, String child) {
        return child.startsWith(parent) && child.length() > parent.length();
    }

}
