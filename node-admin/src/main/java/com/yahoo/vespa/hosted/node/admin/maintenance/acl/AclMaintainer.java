// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.container.metrics.Dimensions;
import com.yahoo.vespa.hosted.node.admin.container.metrics.Metrics;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentTask;
import com.yahoo.vespa.hosted.node.admin.task.util.file.Editor;
import com.yahoo.vespa.hosted.node.admin.task.util.file.LineEditor;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * This class maintains the iptables (ipv4 and ipv6) for all running containers.
 * The filter table is synced with ACLs fetched from the Node repository while the nat table
 * is synched with the proper redirect rule.
 * <p>
 * If an ACL cannot be configured (e.g. iptables process execution fails) we attempted to flush the rules
 * rendering the firewall open.
 * <p>
 * This class currently assumes control over the filter and nat table.
 * <p>
 * The configuration will be retried the next time the maintainer runs.
 *
 * @author mpolden
 * @author smorgrav
 */
public class AclMaintainer {
    private static final Logger logger = Logger.getLogger(AclMaintainer.class.getName());

    private final ContainerOperations containerOperations;
    private final IPAddresses ipAddresses;
    private final Metrics metrics;
    private final Map<String, Long> lastSuccess;
    private static final String METRIC_NAME_POSTFIX = ".acl.age";

    public AclMaintainer(ContainerOperations containerOperations, IPAddresses ipAddresses, Metrics metrics) {
        this.containerOperations = containerOperations;
        this.ipAddresses = ipAddresses;
        this.metrics = metrics;
        long timestamp = System.currentTimeMillis() / 1_000;
        this.lastSuccess = new HashMap<>(Map.of(IPVersion.IPv4.id(), timestamp,
                                                IPVersion.IPv6.id(), timestamp));
    }

    // ip(6)tables operate while having the xtables lock, run with synchronized to prevent multiple NodeAgents
    // invoking ip(6)tables concurrently.
    public synchronized void converge(NodeAgentContext context) {
        if (context.isDisabled(NodeAgentTask.AclMaintainer)) return;

        // Apply acl to the filter table
        boolean updatedIPv4 = editFlushOnError(context, IPVersion.IPv4, "filter", FilterTableLineEditor.from(context.acl(), IPVersion.IPv4));
        boolean updatedIPv6 = editFlushOnError(context, IPVersion.IPv6, "filter", FilterTableLineEditor.from(context.acl(), IPVersion.IPv6));

        Dimensions dimensions = generateDimensions(context);

        updateMetric(dimensions, updatedIPv4, IPVersion.IPv4.id());
        updateMetric(dimensions, updatedIPv6, IPVersion.IPv6.id());

        ipAddresses.getAddress(context.hostname().value(), IPVersion.IPv4).ifPresent(addr -> applyRedirect(context, addr));
        ipAddresses.getAddress(context.hostname().value(), IPVersion.IPv6).ifPresent(addr -> applyRedirect(context, addr));
    }

    private void applyRedirect(NodeAgentContext context, InetAddress address) {
        IPVersion ipVersion = IPVersion.get(address);
        // Necessary to avoid the routing packets destined for the node's own public IP address
        // via the bridge, which is illegal.
        String redirectRule = "-A OUTPUT -d " + InetAddresses.toAddrString(address) + ipVersion.singleHostCidr() + " -j REDIRECT";
        editLogOnError(context, ipVersion, "nat", NatTableLineEditor.from(redirectRule));
    }

    private boolean editFlushOnError(NodeAgentContext context, IPVersion ipVersion, String table, LineEditor lineEditor) {
        return edit(context, table, ipVersion, lineEditor, true);
    }

    private boolean editLogOnError(NodeAgentContext context, IPVersion ipVersion, String table, LineEditor lineEditor) {
        return edit(context, table, ipVersion, lineEditor, false);
    }

    private boolean edit(NodeAgentContext context, String table, IPVersion ipVersion, LineEditor lineEditor, boolean flush) {
        Editor editor = new Editor(
                ipVersion.iptablesCmd() + "-" + table,
                listTable(context, table, ipVersion),
                restoreTable(context, table, ipVersion, flush),
                lineEditor);
        return editor.edit(message -> context.log(logger, message));
    }

    private Supplier<List<String>> listTable(NodeAgentContext context, String table, IPVersion ipVersion) {
        return () -> containerOperations
                .executeCommandInNetworkNamespace(context, ipVersion.iptablesCmd(), "-S", "-t", table)
                .mapEachLine(String::trim);
    }

    private Consumer<List<String>> restoreTable(NodeAgentContext context, String table, IPVersion ipVersion, boolean flush) {
        return list -> {
            try (TemporaryIpTablesFileHandler fileHandler = new TemporaryIpTablesFileHandler(table)) {
                String rules = String.join("\n", list);
                String fileContent = "*" + table + "\n" + rules + "\nCOMMIT\n";
                fileHandler.writeUtf8Content(fileContent);
                containerOperations.executeCommandInNetworkNamespace(context, ipVersion.iptablesRestore(), fileHandler.absolutePath());
            } catch (Exception e) {
                if (flush) {
                    context.log(logger, Level.SEVERE, "Exception occurred while syncing iptable " + table + ", attempting rollback", e);
                    try {
                        containerOperations.executeCommandInNetworkNamespace(context, ipVersion.iptablesCmd(), "-F", "-t", table);
                    } catch (Exception ne) {
                        context.log(logger, Level.SEVERE, "Rollback of table " + table + " failed, giving up", ne);
                    }
                } else {
                    context.log(logger, Level.WARNING, "Unable to sync iptables for " + table, e);
                }
            }
        };
    }

    void updateMetric(Dimensions dimensions, boolean updated, String ipVersion) {
        long updateAgeInSec;
        long timestamp = System.currentTimeMillis() / 1_000;
        if (updated) {
            updateAgeInSec = 0;
            lastSuccess.put(ipVersion, timestamp);
        } else {
            updateAgeInSec = timestamp - lastSuccess.get(ipVersion);
        }

        metrics.declareGauge(Metrics.APPLICATION_NODE, ipVersion + METRIC_NAME_POSTFIX, dimensions, Metrics.DimensionType.PRETAGGED)
                .sample(updateAgeInSec);

    }

    private Dimensions generateDimensions(NodeAgentContext context) {
        NodeSpec node = context.node();
        Dimensions.Builder dimensionsBuilder = new Dimensions.Builder()
                .add("host", node.hostname())
                .add("zone", context.zone().getId().value());

        node.currentVespaVersion().ifPresent(vespaVersion -> dimensionsBuilder.add("vespaVersion", vespaVersion.toFullString()));

        return dimensionsBuilder.build();
    }

    private static class TemporaryIpTablesFileHandler implements AutoCloseable {
        private final Path path;

        private TemporaryIpTablesFileHandler(String table) {
            this.path = uncheck(() -> Files.createTempFile("iptables-restore", "." + table));
        }

        private void writeUtf8Content(String content) throws IOException {
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        }

        private String absolutePath() {
            return path.toAbsolutePath().toString();
        }

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
