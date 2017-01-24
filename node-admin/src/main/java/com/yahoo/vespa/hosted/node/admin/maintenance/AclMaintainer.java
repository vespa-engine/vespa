package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.google.common.net.InetAddresses;
import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.ContainerAclSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.util.IpTables;
import com.yahoo.vespa.hosted.node.admin.util.IpTables.Policy;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.net.Inet6Address;
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
 * If a rule cannot be configured (e.g. iptables process execution fails), a rollback is attempted by setting the
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

    private final DockerOperations dockerOperations;
    private final NodeRepository nodeRepository;
    private final Supplier<String> nodeAdminHostnameSupplier;

    public AclMaintainer(DockerOperations dockerOperations, NodeRepository nodeRepository) {
        this(dockerOperations, nodeRepository, HostName::getLocalhost);
    }

    AclMaintainer(DockerOperations dockerOperations, NodeRepository nodeRepository,
                  Supplier<String> nodeAdminHostnameSupplier) {
        this.dockerOperations = dockerOperations;
        this.nodeRepository = nodeRepository;
        this.nodeAdminHostnameSupplier = nodeAdminHostnameSupplier;
    }

    private void applyAcl(ContainerName containerName, List<ContainerAclSpec> aclSpecs) {
        try {
            dockerOperations.executeCommandInNetworkNamespace(containerName, IpTables.flushChain());
            aclSpecs.stream()
                    .map(ContainerAclSpec::ipAddress)
                    .filter(AclMaintainer::isIpv6)
                    .forEach(ipAddress -> dockerOperations.executeCommandInNetworkNamespace(containerName,
                            IpTables.allowFromAddress(ipAddress)));
            dockerOperations.executeCommandInNetworkNamespace(containerName, IpTables.chainPolicy(Policy.DROP));
        } catch (Exception e) {
            log.error("Exception occurred while configuring ACLs, attempting rollback", e);
            try {
                dockerOperations.executeCommandInNetworkNamespace(containerName, IpTables.chainPolicy(Policy.ACCEPT));
            } catch (Exception ne) {
                log.error("Rollback failed, giving up", ne);
            }
        }
    }

    private void configureAcls() {
        final List<ContainerAclSpec> aclSpecs = nodeRepository.getContainerAclSpecs(nodeAdminHostnameSupplier.get());
        final Map<String, List<ContainerAclSpec>> aclSpecsGroupedByHostname = aclSpecs.stream()
                .collect(Collectors.groupingBy(ContainerAclSpec::trustedBy));

        for (Map.Entry<String, List<ContainerAclSpec>> entry : aclSpecsGroupedByHostname.entrySet()) {
            final String hostname = entry.getKey();
            final Optional<Container> container = dockerOperations.getContainer(hostname);
            if (!container.isPresent()) {
                // Container belongs to this Docker host, but is currently unallocated
                continue;
            }
            if (!container.get().isRunning) {
                log.info("Skipping ACL configuration for stopped container " + container.get().hostname);
                continue;
            }
            applyAcl(container.get().name, entry.getValue());
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

    private static boolean isIpv6(String ipAddress) {
        return InetAddresses.forString(ipAddress) instanceof Inet6Address;
    }
}
