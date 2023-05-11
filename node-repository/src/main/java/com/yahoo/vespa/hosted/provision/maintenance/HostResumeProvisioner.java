// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
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
        NodeList allNodes = nodeRepository().nodes().list();
        NodeList hosts = allNodes.state(Node.State.provisioned).nodeType(NodeType.host, NodeType.confighost, NodeType.controllerhost);
        int failures = 0;
        for (Node host : hosts) {
            NodeList children = allNodes.childrenOf(host);
            try {
                HostIpConfig hostIpConfig = hostProvisioner.provision(host, children.asSet());
                setIpConfig(host, children, hostIpConfig);
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
        return asSuccessFactorDeviation(hosts.size(), failures);
    }

    private void setIpConfig(Node host, NodeList children, HostIpConfig hostIpConfig) {
        if (hostIpConfig.isEmpty()) return;
        NodeList nodes = NodeList.of(host).and(children);
        for (var node : nodes) {
            verifyDns(node, hostIpConfig.require(node.hostname()));
        }
        nodeRepository().nodes().setIpConfig(hostIpConfig);
    }

    /** Verify DNS configuration of given node */
    private void verifyDns(Node node, IP.Config ipConfig) {
        for (var ipAddress : ipConfig.primary()) {
            IP.verifyDns(node.hostname(), ipAddress, nodeRepository().nameResolver(), verifyPtr(node, ipAddress));
        }
    }

    private boolean verifyPtr(Node node, String address) {
        if (node.cloudAccount().isEnclave(nodeRepository().zone())) return false;
        if (nodeRepository().zone().cloud().name().equals(CloudName.GCP) && IP.isV6(address)) return false;
        return true;
    }

}
