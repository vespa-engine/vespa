// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.HostIpConfig;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.yolean.Exceptions;

import javax.naming.NamingException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        NodeList allNodes;
        // Host and child nodes are written in separate transactions, but both are written while holding the
        // unallocated lock. Hold the unallocated lock while reading nodes to ensure we get all the children
        // of newly provisioned hosts.
        try (Mutex ignored = nodeRepository().nodes().lockUnallocated()) {
            allNodes = nodeRepository().nodes().list();
        }

        NodeList hosts = allNodes.state(Node.State.provisioned).nodeType(NodeType.host, NodeType.confighost, NodeType.controllerhost);
        int failures = 0;
        for (Node host : hosts) {
            try {
                HostIpConfig hostIpConfig = hostProvisioner.provision(host);
                setIpConfig(host, hostIpConfig);
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.log(Level.INFO, "Could not provision " + host.hostname() + ", will retry in " +
                                    interval() + ": " + Exceptions.toMessageString(e));
            } catch (FatalProvisioningException e) {
                failures++;
                log.log(Level.SEVERE, "Failed to provision " + host.hostname() + ", failing out the host recursively", e);
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
        return asSuccessFactorDeviation(hosts.size(), failures);
    }

    private void setIpConfig(Node host, HostIpConfig hostIpConfig) {
        if (hostIpConfig.isEmpty()) return;
        hostIpConfig.asMap().forEach((hostname, ipConfig) ->
                verifyDns(hostname, host.type(), host.cloudAccount(), ipConfig));
        nodeRepository().nodes().setIpConfig(hostIpConfig);
    }

    /** Verify DNS configuration of given node */
    private void verifyDns(String hostname, NodeType hostType, CloudAccount cloudAccount, IP.Config ipConfig) {
        for (String ipAddress : ipConfig.primary()) {
            IP.verifyDns(hostname, ipAddress, hostType, nodeRepository().nameResolver(), cloudAccount, nodeRepository().zone());
        }
    }

}
