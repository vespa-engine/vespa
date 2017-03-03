package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.ContainerAclSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.Action;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.Chain;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.Command;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.FlushCommand;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.iptables.PolicyCommand;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The responsibility of this class is to configure ACLs for all running containers. The ACLs are fetched from the Node
 * repository. Based on those ACLs, iptables commands are created and then executed in each of the containers network
 * namespace.
 *
 * If an ACL cannot be configured (e.g. iptables process execution fails), a rollback is attempted by setting the
 * default policy to ACCEPT which will allow any traffic. The configuration will be retried the next time the
 * maintainer runs.
 *
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
    private final Supplier<String> nodeAdminHostnameSupplier;
    private final Map<ContainerName, Acl> containerAcls;

    public AclMaintainer(DockerOperations dockerOperations, NodeRepository nodeRepository) {
        this(dockerOperations, nodeRepository, HostName::getLocalhost);
    }

    AclMaintainer(DockerOperations dockerOperations, NodeRepository nodeRepository,
                  Supplier<String> nodeAdminHostnameSupplier) {
        this.dockerOperations = dockerOperations;
        this.nodeRepository = nodeRepository;
        this.nodeAdminHostnameSupplier = nodeAdminHostnameSupplier;
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
            dockerOperations.executeCommandInNetworkNamespace(containerName, flush.asArray(IPTABLES_COMMAND));
            acl.toCommands().forEach(command -> dockerOperations.executeCommandInNetworkNamespace(containerName,
                    command.asArray(IPTABLES_COMMAND)));
            containerAcls.put(containerName, acl);
        } catch (Exception e) {
            log.error("Exception occurred while configuring ACLs, attempting rollback", e);
            try {
                dockerOperations.executeCommandInNetworkNamespace(containerName, rollback.asArray(IPTABLES_COMMAND));
            } catch (Exception ne) {
                log.error("Rollback failed, giving up", ne);
            }
        }
    }

    private void configureAcls() {
        final List<ContainerAclSpec> aclSpecs = nodeRepository.getContainerAclSpecs(nodeAdminHostnameSupplier.get());
        final Map<ContainerName, List<ContainerAclSpec>> aclSpecsGroupedByHostname = aclSpecs.stream()
                .collect(Collectors.groupingBy(ContainerAclSpec::trustedBy));

        for (Map.Entry<ContainerName, List<ContainerAclSpec>> entry : aclSpecsGroupedByHostname.entrySet()) {
            final ContainerName containerName = entry.getKey();
            final Optional<Container> container = dockerOperations.getContainer(containerName);
            if (!container.isPresent()) {
                // Container belongs to this Docker host, but is currently unallocated
                continue;
            }
            if (!container.get().state.isRunning()) {
                log.info(String.format("Container with name %s is not running, skipping", container.get().name.asString()));
                continue;
            }
            applyAcl(container.get().name, new Acl(container.get().pid, entry.getValue()));
        }
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
