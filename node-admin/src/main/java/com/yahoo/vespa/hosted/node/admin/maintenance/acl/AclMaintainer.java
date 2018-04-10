// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The responsibility of this class is to configure ACLs for all running containers. The ACLs are fetched from the Node
 * repository. Based on those ACLs, iptables commands are created and then executed in each of the containers network
 * namespace.
 * <p>
 * If an ACL cannot be configured (e.g. iptables process execution fails) we attempted to flush the rules
 * rendering the firewall open.
 * <p>
 * The configuration will be retried the next time the maintainer runs.
 *
 * @author mpolden
 * @author smorgrav
 */
public class AclMaintainer implements Runnable {

    private static final PrefixLogger log = PrefixLogger.getNodeAdminLogger(AclMaintainer.class);

    private final DockerOperations dockerOperations;
    private final NodeRepository nodeRepository;
    private final IPAddresses ipAddresses;
    private final String nodeAdminHostname;

    public AclMaintainer(DockerOperations dockerOperations, NodeRepository nodeRepository, String nodeAdminHostname, IPAddresses ipAddresses) {
        this.dockerOperations = dockerOperations;
        this.nodeRepository = nodeRepository;
        this.ipAddresses = ipAddresses;
        this.nodeAdminHostname = nodeAdminHostname;
    }

    private void apply(Container container, Acl acl, IPVersion ipVersion) {

        // 1. Get address
        Optional<InetAddress> address = ipAddresses.getAddress(container.hostname, ipVersion);
        if (!address.isPresent()) return;

        // 2. Generated wanted/expected iptables
        String wantedFilterTable = acl.toListRules(address.get());

        File file = null;
        try {
            // 3. Get current iptables
            ProcessResult currentFilterTableResult =
                    dockerOperations.executeCommandInNetworkNamespace(container.name, ipVersion.iptablesCmd() + " -S -t filter");
            String currentFilterTable = currentFilterTableResult.getOutput();

            // 4. Compare and apply wanted if different
            if (!wantedFilterTable.equals(currentFilterTable)) {
                String command = acl.toRestoreCommand(address.get());
                file = writeTempFile(ipVersion.name(), command);
                dockerOperations.executeCommandInNetworkNamespace(container.name, ipVersion.iptablesCmd() + "-restore " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            String rollbackCmd = ipVersion.iptablesCmd() + " -F";
            log.error("Exception occurred while configuring ACLs for " + container.name.asString() + ", attempting rollback", e);
            try {
                dockerOperations.executeCommandInNetworkNamespace(container.name, rollbackCmd);
            } catch (Exception ne) {
                log.error("Rollback of ACLs for " + container.name.asString() + " failed, giving up", ne);
            }
        } finally {
            if (file != null) {
                file.delete();
            }
        }
    }

    private File writeTempFile(String postfix, String content) {
        try {
            Path path = Files.createTempFile("restore", "." + postfix);
            File file = path.toFile();
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            file.deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new RuntimeException("Unable to write restore file for iptables.", e);
        }
    }

    private void apply(Container container, Acl acl) {
        apply(container, acl, IPVersion.IPv6);
        apply(container, acl, IPVersion.IPv4);
    }

    private synchronized void configureAcls() {
        Map<String, Container> runningContainers = dockerOperations
                .getAllManagedContainers().stream()
                .filter(container -> container.state.isRunning())
                .collect(Collectors.toMap(container -> container.name.asString(), container -> container));

        nodeRepository.getAcl(nodeAdminHostname, runningContainers.keySet())
                .forEach((containerName, acl) -> apply(runningContainers.get(containerName), acl));
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
