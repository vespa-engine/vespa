// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Authorizer for config server REST APIs. This contains the rules for all API paths where the authorization process
 * requires information from the node-repository to make a decision
 *
 * @author mpolden
 */
public class Authorizer implements BiPredicate<Principal, URI> {

    private final SystemName system;
    private final NodeRepository nodeRepository;
    private final Supplier<String> hostnameSupplier;

    public Authorizer(SystemName system, NodeRepository nodeRepository) {
        this(system, nodeRepository, HostName::getLocalhost);
    }

    Authorizer(SystemName system, NodeRepository nodeRepository, Supplier<String> hostnameSupplier) {
        this.system = system;
        this.nodeRepository = nodeRepository;
        this.hostnameSupplier = hostnameSupplier;
    }

    /** Returns whether principal is authorized to access given URI */
    @Override
    public boolean test(Principal principal, URI uri) {
        // Trusted services can access everything
        if (principal.getName().equals(trustedService())) {
            return true;
        }

        // Individual nodes can only access their own resources
        if (canAccessAll(hostnamesFrom(uri), principal, this::isSelfOrParent)) {
            return true;
        }

        // Nodes can access this resource if its type matches any of the valid node types
        if (canAccessAny(nodeTypesFor(uri), principal, this::isNodeType)) {
            return true;
        }

        // The host itself can access all resources
        if (isLocalhost(principal)) {
            return true;
        }

        return false;
    }

    /** Returns whether principal is the node itself or the parent of the node */
    private boolean isSelfOrParent(String hostname, Principal principal) {
        // Node can always access itself
        if (principal.getName().equals(hostname)) {
            return true;
        }

        // Parent node can access its children
        return getNode(hostname).flatMap(Node::parentHostname)
                                .map(parentHostname -> principal.getName().equals(parentHostname))
                                .orElse(false);
    }

    /** Returns whether principal is a node of the given node type */
    private boolean isNodeType(NodeType type, Principal principal) {
        return getNode(principal.getName()).map(node -> node.type() == type)
                                           .orElse(false);
    }

    /** Returns whether given principal is the hostname of this node */
    private boolean isLocalhost(Principal principal) {
        return principal.getName().equals(hostnameSupplier.get());
    }

    /** Returns whether principal can access all given resources */
    private <T> boolean canAccessAll(List<T> resources, Principal principal, BiPredicate<T, Principal> predicate) {
        return !resources.isEmpty() && resources.stream().allMatch(resource -> predicate.test(resource, principal));
    }

    /** Returns whether principal can access any of the given resources */
    private <T> boolean canAccessAny(List<T> resources, Principal principal, BiPredicate<T, Principal> predicate) {
        return !resources.isEmpty() && resources.stream().anyMatch(resource -> predicate.test(resource, principal));
    }

    /** Trusted service name for this system */
    private String trustedService() {
        if (system != SystemName.main) {
            return "vespa.vespa." + system.name() + ".hosting";
        }
        return "vespa.vespa.hosting";
    }

    private Optional<Node> getNode(String hostname) {
        // Ignore potential path traversal. Node repository happily passes arguments unsanitized all the way down to
        // curator...
        if (hostname.chars().allMatch(c -> c == '.')) {
            return Optional.empty();
        }
        return nodeRepository.getNode(hostname);
    }

    /** Returns hostnames contained in query parameters of given URI */
    private static List<String> hostnamesFromQuery(URI uri) {
        return URLEncodedUtils.parse(uri, StandardCharsets.UTF_8.name())
                              .stream()
                              .filter(pair -> "hostname".equals(pair.getName()) ||
                                              "parentHost".equals(pair.getName()))
                              .map(NameValuePair::getValue)
                              .filter(hostname -> !hostname.isEmpty())
                              .collect(Collectors.toList());
    }

    /** Returns hostnames from a URI if any, e.g. /nodes/v2/node/node1.fqdn */
    private static List<String> hostnamesFrom(URI uri) {
        if (isChildOf("/nodes/v2/acl/", uri.getPath()) ||
            isChildOf("/nodes/v2/node/", uri.getPath()) ||
            isChildOf("/nodes/v2/state/", uri.getPath())) {
            return Collections.singletonList(lastChildOf(uri.getPath()));
        }
        if (isChildOf("/orchestrator/v1/hosts/", uri.getPath())) {
            return firstChildOf("/orchestrator/v1/hosts/", uri.getPath())
                    .map(Collections::singletonList)
                    .orElseGet(Collections::emptyList);
        }
        if (isChildOf("/orchestrator/v1/suspensions/hosts/", uri.getPath())) {
            List<String> hostnames = new ArrayList<>();
            hostnames.add(lastChildOf(uri.getPath()));
            hostnames.addAll(hostnamesFromQuery(uri));
            return hostnames;
        }
        if (isChildOf("/nodes/v2/command/", uri.getPath()) ||
            "/nodes/v2/node/".equals(uri.getPath())) {
            return hostnamesFromQuery(uri);
        }
        return Collections.emptyList();
    }

    /** Returns node types which can access given URI */
    private static List<NodeType> nodeTypesFor(URI uri) {
        if (isChildOf("/routing/v1/", uri.getPath())) {
            return Arrays.asList(NodeType.proxy, NodeType.proxyhost);
        }

        if ("/nodes/v2/node/".equals(uri.getPath())) {
            Set<String> nodeTypeFilters = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8.name()).stream()
                    .filter(pair -> "type".equals(pair.getName()))
                    .map(NameValuePair::getValue)
                    .collect(Collectors.toSet());

            // Config server hosts needs to access state of all config servers
            if (nodeTypeFilters.equals(Collections.singleton(NodeType.config.name()))) {
                return Collections.singletonList(NodeType.confighost);
            }
        }
        return Collections.emptyList();
    }

    /** Returns whether child is a sub-path of parent */
    private static boolean isChildOf(String parent, String child) {
        return child.startsWith(parent) && child.length() > parent.length();
    }

    /** Returns the first component of path relative to root */
    private static Optional<String> firstChildOf(String root, String path) {
        if (!isChildOf(root, path)) {
            return Optional.empty();
        }
        path = path.substring(root.length(), path.length());
        int firstSeparator = path.indexOf('/');
        if (firstSeparator == -1) {
            return Optional.of(path);
        }
        return Optional.of(path.substring(0, firstSeparator));
    }

    /** Returns the last component of the given path */
    private static String lastChildOf(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSeparator = path.lastIndexOf("/");
        if (lastSeparator == -1) {
            return path;
        }
        return path.substring(lastSeparator + 1, path.length());
    }

}
