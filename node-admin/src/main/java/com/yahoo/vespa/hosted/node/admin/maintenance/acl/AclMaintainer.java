// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.NodeAcl;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.Action;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.Chain;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.Command;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.FlushCommand;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.PolicyCommand;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The responsibility of this class is to configure ACLs for all running containers. The ACLs are fetched from the Node
 * repository. Based on those ACLs, iptables commands are created and then executed in each of the containers network
 * namespace.
 * <p>
 * If an ACL cannot be configured (e.g. iptables process execution fails), a rollback is attempted by setting the
 * default policy to ACCEPT which will allow any traffic. The configuration will be retried the next time the
 * maintainer runs.
 * <p>
 * The ACL maintainer does not handle IPv4 addresses and is thus only intended to configure ACLs for IPv6-only
 * containers (e.g. any container, except node-admin).
 *
 * @author mpolden
 */
public class AclMaintainer implements Runnable {

    private static final PrefixLogger log = PrefixLogger.getNodeAdminLogger(AclMaintainer.class);
    private static final String IPTABLES_COMMAND = "ip6tables";

    private final DockerOperations dockerOperations;
    private final NodeRepository nodeRepository;
    private final String nodeAdminHostname;
    private final Map<ContainerName, Acl> containerAcls;

    public AclMaintainer(DockerOperations dockerOperations, NodeRepository nodeRepository,
                         String nodeAdminHostname) {
        this.dockerOperations = dockerOperations;
        this.nodeRepository = nodeRepository;
        this.nodeAdminHostname = nodeAdminHostname;
        this.containerAcls = new HashMap<>();
    }

    private boolean isAclActive(ContainerName containerName, Acl acl) {
        return Optional.ofNullable(containerAcls.get(containerName))
                .map(acl::equals)
                .orElse(false);
    }

    private void applyAcl(ContainerName containerName, Acl acl) {
        if (isAclActive(containerName, acl)) {
            return;
        }
        final Command flush = new FlushCommand(Chain.INPUT);
        final Command rollback = new PolicyCommand(Chain.INPUT, Action.ACCEPT);
        try {
            String commands = Stream.concat(Stream.of(flush), acl.toCommands().stream())
                    .map(command -> command.asString(IPTABLES_COMMAND))
                    .collect(Collectors.joining("; "));

            log.debug("Running ACL command '" + commands + "' in " + containerName.asString());
            dockerOperations.executeCommandInNetworkNamespace(containerName, "/bin/sh", "-c", commands);
            containerAcls.put(containerName, acl);
        } catch (Exception e) {
            log.error("Exception occurred while configuring ACLs for " + containerName.asString() + ", attempting rollback", e);
            try {
                dockerOperations.executeCommandInNetworkNamespace(containerName, rollback.asArray(IPTABLES_COMMAND));
            } catch (Exception ne) {
                log.error("Rollback of ACLs for " + containerName.asString() + " failed, giving up", ne);
            }
        }
    }

    private synchronized void configureAcls() {
        final Map<ContainerName, List<NodeAcl>> nodeAclGroupedByContainerName = nodeRepository
                .getNodeAcl(nodeAdminHostname).stream()
                .collect(Collectors.groupingBy(NodeAcl::trustedBy));

        dockerOperations
                .getAllManagedContainers().stream()
                .filter(container -> container.state.isRunning())
                .map(container -> new Pair<>(container, nodeAclGroupedByContainerName.get(container.name)))
                .filter(pair -> pair.getSecond() != null)
                .forEach(pair ->
                        applyAcl(pair.getFirst().name, new Acl(pair.getFirst().pid, pair.getSecond())));
    }

    @Override
    public void run() {
        try {
            configureAcls();
        } catch (Throwable t) {
            log.error("Failed to configure ACLs", t);
        }
    }
}
