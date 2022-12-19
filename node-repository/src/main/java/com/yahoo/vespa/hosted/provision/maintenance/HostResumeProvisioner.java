// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.yolean.Exceptions;

import javax.naming.NamingException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Resumes provisioning (requests additional IP addresses, updates DNS when IPs are ready) of hosts in state provisioned
 *
 * @author freva
 * @author mpolden
 */
public class HostResumeProvisioner extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(HostResumeProvisioner.class.getName());

    private final HostProvisioner hostProvisioner;

    HostResumeProvisioner(NodeRepository nodeRepository, Duration interval, Metric metric, HostProvisioner hostProvisioner) {
        super(nodeRepository, interval, metric);
        this.hostProvisioner = hostProvisioner;
    }

    @Override
    protected double maintain() {
        NodeList allNodes = nodeRepository().nodes().list();
        Map<String, Set<Node>> nodesByProvisionedParentHostname =
                allNodes.nodeType(NodeType.tenant, NodeType.config, NodeType.controller)
                     .asList()
                     .stream()
                     .filter(node -> node.parentHostname().isPresent())
                     .collect(Collectors.groupingBy(node -> node.parentHostname().get(), Collectors.toSet()));

        NodeList hosts = allNodes.state(Node.State.provisioned).nodeType(NodeType.host, NodeType.confighost, NodeType.controllerhost);
        int failures = 0;
        for (Node host : hosts) {
            Set<Node> children = nodesByProvisionedParentHostname.getOrDefault(host.hostname(), Set.of());
            // This doesn't actually require unallocated lock, but that is much easier than simultaneously holding
            // the application locks of the host and all it's children.
            try (var lock = nodeRepository().nodes().lockUnallocated()) {
                List<Node> updatedNodes = hostProvisioner.provision(host, children);
                verifyDns(updatedNodes);
                nodeRepository().nodes().write(updatedNodes, lock);
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.log(Level.INFO, "Could not provision " + host.hostname() + " with " + children.size() + " children, will retry in " +
                                    interval() + ": " + Exceptions.toMessageString(e));
            } catch (FatalProvisioningException e) {
                failures++;
                log.log(Level.SEVERE, "Failed to provision " + host.hostname() + " with " + children.size()  +
                                      " children, failing out the host recursively", e);
                nodeRepository().nodes().failOrMarkRecursively(
                        host.hostname(), Agent.HostResumeProvisioner, "Failed by HostResumeProvisioner due to provisioning failure");
            } catch (RuntimeException e) {
                if (e.getCause() instanceof NamingException)
                    log.log(Level.INFO, "Could not provision " + host.hostname() + ", will retry in " + interval() + ": " + Exceptions.toMessageString(e));
                else {
                    failures++;
                    log.log(Level.WARNING, "Failed to provision " + host.hostname() + ", will retry in " + interval(), e);
                }
            }
        }
        return asSuccessFactor(hosts.size(), failures);
    }

    /** Verify DNS configuration of given nodes */
    private void verifyDns(List<Node> nodes) {
        for (var node : nodes) {
            boolean enclave = node.cloudAccount().isEnclave(nodeRepository().zone());
            for (var ipAddress : node.ipConfig().primary()) {
                IP.verifyDns(node.hostname(), ipAddress, nodeRepository().nameResolver(), !enclave);
            }
        }
    }
}
