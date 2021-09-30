// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status.statuspage;

import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Renders web page with cluster status.
 */
public class VdsClusterHtmlRenderer {

    private static final TimeZone utcTimeZone = TimeZone.getTimeZone("UTC");

    public static class Table {
        private final HtmlTable table = new HtmlTable();
        private final HtmlTable.CellProperties headerProperties;
        private final StringBuilder contentBuilder = new StringBuilder();
        private final static String TAG_NOT_SET = "not set";
        private final static HtmlTable.CellProperties WARNING_PROPERTY = new HtmlTable.CellProperties().setBackgroundColor(0xffffc0);
        private final static HtmlTable.CellProperties ERROR_PROPERTY = new HtmlTable.CellProperties().setBackgroundColor(0xffc0c0);
        private final static HtmlTable.CellProperties CENTERED_PROPERTY = new HtmlTable.CellProperties().align(HtmlTable.Orientation.CENTER);

        Table(final String clusterName, final int slobrokGenerationCount) {
            table.getTableProperties().align(HtmlTable.Orientation.RIGHT).setBackgroundColor(0xc0ffc0);
            table.getColProperties(0).align(HtmlTable.Orientation.CENTER).setBackgroundColor(0xffffff);
            table.getColProperties(1).align(HtmlTable.Orientation.LEFT);
            table.getColProperties(2).align(HtmlTable.Orientation.LEFT);
            table.getColProperties(3).align(HtmlTable.Orientation.LEFT);
            table.getColProperties(7).align(HtmlTable.Orientation.LEFT);
            table.getColProperties(14).align(HtmlTable.Orientation.LEFT);
            for (int i = 4; i < 15; ++i) table.getColProperties(i).allowLineBreaks(false);
            headerProperties = new HtmlTable.CellProperties()
                    .setBackgroundColor(0xffffff)
                    .align(HtmlTable.Orientation.CENTER);
            contentBuilder.append("<h2>State of content cluster '")
                    .append(clusterName)
                    .append("'.</h2>\n")
                    .append("<p>Based on information retrieved from slobrok at generation ")
                    .append(slobrokGenerationCount).append(".</p>\n");
        }

        public void appendRaw(String rawHtml) {
            contentBuilder.append(rawHtml);
        }

        public void addTable(final StringBuilder destination, final long stableStateTimePeriode) {
            destination.append(contentBuilder);

            destination.append(table.toString())
                    .append("<p>")
                    .append("<p>");
            addFooter(destination, stableStateTimePeriode);
        }

        public void renderNodes(
                final TreeMap<Integer, NodeInfo> storageNodeInfos,
                final TreeMap<Integer, NodeInfo> distributorNodeInfos,
                final Timer timer,
                final ClusterStateBundle state,
                final ClusterStatsAggregator statsAggregator,
                final double minMergeCompletionRatio,
                final int maxPrematureCrashes,
                final Map<String, Double> feedBlockLimits,
                final EventLog eventLog,
                final String pathPrefix,
                final String name) {
            final String dominantVtag = findDominantVtag(
                    storageNodeInfos, distributorNodeInfos);

            renderNodesOneType(storageNodeInfos,
                    NodeType.STORAGE,
                    timer,
                    state,
                    statsAggregator,
                    minMergeCompletionRatio,
                    maxPrematureCrashes,
                    feedBlockLimits,
                    eventLog,
                    pathPrefix,
                    dominantVtag,
                    name);
            renderNodesOneType(distributorNodeInfos,
                    NodeType.DISTRIBUTOR,
                    timer,
                    state,
                    statsAggregator,
                    minMergeCompletionRatio,
                    maxPrematureCrashes,
                    feedBlockLimits,
                    eventLog,
                    pathPrefix,
                    dominantVtag,
                    name);
        }

        private String findDominantVtag(
                final Map<Integer, NodeInfo> storageNodeInfos,
                final Map<Integer, NodeInfo> distributorNodeInfos) {
            final List<NodeInfo> nodeInfos = new ArrayList<>();
            nodeInfos.addAll(storageNodeInfos.values());
            nodeInfos.addAll(distributorNodeInfos.values());

            final Map<String, Integer> versionTagToCount = new HashMap<>();
            int maxCount = -1;
            String dominantVtag = null;
            for (NodeInfo nodeInfo : nodeInfos) {
                final String buildTag = nodeInfo.getVtag();
                Integer count = versionTagToCount.get(buildTag);
                count = count == null ? 1 : count + 1;
                versionTagToCount.put(buildTag, count);
                if (count > maxCount) {
                    maxCount = count;
                    dominantVtag = buildTag;
                }
            }
            return dominantVtag == null ? TAG_NOT_SET : dominantVtag;
        }
        private void addTableHeader(final String name, final NodeType nodeType) {
            table.addRow(new HtmlTable.Row().addCell(
                    new HtmlTable.Cell("Group " + name)
                            .addProperties(new HtmlTable.CellProperties()
                                    .setColSpan(0)
                                    .setBackgroundColor(0xccccff)
                                    .align(HtmlTable.Orientation.LEFT))));
            table.addRow(new HtmlTable.Row()
                    .setHeaderRow()
                    .addProperties(headerProperties)
                    .addProperties(new HtmlTable.CellProperties().setRowSpan(2))
                    .addCell(new HtmlTable.Cell(nodeType == NodeType.DISTRIBUTOR ? "Distributor" : "Storage"))
                    .addCell(new HtmlTable.Cell("Node states")
                            .addProperties(new HtmlTable.CellProperties().setColSpan(3).setRowSpan(1)))
                    .addCell(new HtmlTable.Cell("Build"))
                    .addCell(new HtmlTable.Cell("FC<sup>1)</sup>"))
                    .addCell(new HtmlTable.Cell("OCT<sup>2)</sup>"))
                    .addCell(new HtmlTable.Cell("SPT<sup>3)</sup>"))
                    .addCell(new HtmlTable.Cell("SSV<sup>4)</sup>"))
                    .addCell(new HtmlTable.Cell("PC<sup>5)</sup>"))
                    .addCell(new HtmlTable.Cell("ELW<sup>6)</sup>"))
                    .addCell(new HtmlTable.Cell(FixedBucketSpaces.defaultSpace() + " buckets")
                            .addProperties(new HtmlTable.CellProperties().setColSpan(2).setRowSpan(1)))
                    .addCell(new HtmlTable.Cell(FixedBucketSpaces.globalSpace() + " buckets")
                            .addProperties(new HtmlTable.CellProperties().setColSpan(2).setRowSpan(1)))
                    .addCell(new HtmlTable.Cell("Resource usage (%)")
                            .addProperties(new HtmlTable.CellProperties().setColSpan(2).setRowSpan(1)))
                    .addCell(new HtmlTable.Cell("Start Time"))
                    .addCell(new HtmlTable.Cell("RPC Address")));
            table.addRow(new HtmlTable.Row().setHeaderRow().addProperties(headerProperties)
                    .addCell(new HtmlTable.Cell("Reported"))
                    .addCell(new HtmlTable.Cell("Wanted"))
                    .addCell(new HtmlTable.Cell("System"))
                    .addCell(new HtmlTable.Cell("Pending"))
                    .addCell(new HtmlTable.Cell("Total"))
                    .addCell(new HtmlTable.Cell("Pending"))
                    .addCell(new HtmlTable.Cell("Total"))
                    .addCell(new HtmlTable.Cell("Disk"))
                    .addCell(new HtmlTable.Cell("Memory")));
        }

        private void renderNodesOneType(
                final TreeMap<Integer, NodeInfo> nodeInfos,
                final NodeType nodeType,
                final Timer timer,
                final ClusterStateBundle stateBundle,
                final ClusterStatsAggregator statsAggregator,
                final double minMergeCompletionRatio,
                final int maxPrematureCrashes,
                final Map<String, Double> feedBlockLimits,
                final EventLog eventLog,
                final String pathPrefix,
                final String dominantVtag,
                final String name) {
            final ClusterState state = stateBundle.getBaselineClusterState();
            final long currentTime = timer.getCurrentTimeInMillis();
            addTableHeader(name, nodeType);
            for (final NodeInfo nodeInfo : nodeInfos.values()) {
                HtmlTable.Row row = new HtmlTable.Row();
                long timeSinceContact = nodeInfo.getTimeOfFirstFailingConnectionAttempt() == 0
                        ? 0 : currentTime - nodeInfo.getTimeOfFirstFailingConnectionAttempt();

                addNodeIndex(pathPrefix, nodeInfo, row);
                addReportedState(nodeInfo, row);
                addWantedState(nodeInfo, row);
                addCurrentState(state, nodeInfo, row);
                addBuildTagVersion(dominantVtag, nodeInfo, row);
                addFailedConnectionAttemptCount(nodeInfo, row, timeSinceContact);
                addTimeSinceFirstFailing(nodeInfo, row, timeSinceContact);
                addStatePendingTime(currentTime, nodeInfo, row);
                addClusterStateVersion(stateBundle, nodeInfo, row);
                addPrematureCrashes(maxPrematureCrashes, nodeInfo, row);
                addEventsLastWeek(eventLog, currentTime, nodeInfo, row);
                addBucketSpacesStats(nodeType, statsAggregator, minMergeCompletionRatio, nodeInfo, row);
                addResourceUsage(nodeInfo, feedBlockLimits, row);
                addStartTime(nodeInfo, row);
                addRpcAddress(nodeInfo, row);

                table.addRow(row);
                if (nodeType.equals(NodeType.STORAGE)) {
                    addFeedBlockedRowIfNodeIsBlocking(stateBundle, nodeInfo, row);
                }
            }
        }

        private void addFeedBlockedRowIfNodeIsBlocking(ClusterStateBundle stateBundle, NodeInfo nodeInfo, HtmlTable.Row nodeRow) {
            // We only show a feed block row if the node is actually blocking feed in the cluster, not
            // just if limits have been exceeded (as feed block may be config disabled).
            // O(n) but n expected to be 0-(very small number) in all realistic cases.
            if (stateBundle.clusterFeedIsBlocked()) {
                var exhaustions = stateBundle.getFeedBlockOrNull().getConcreteExhaustions().stream()
                        .filter(ex -> ex.node.getIndex() == nodeInfo.getNodeIndex())
                        .collect(Collectors.toList());
                if (!exhaustions.isEmpty()) {
                    var exhaustionsDesc = exhaustions.stream()
                            .map(NodeResourceExhaustion::toShorthandDescription)
                            .collect(Collectors.joining(", "));

                    HtmlTable.Row feedBlockRow = new HtmlTable.Row();
                    var contents = String.format("<strong>Node is blocking feed: %s</strong>", HtmlTable.escape(exhaustionsDesc));
                    var cell = new HtmlTable.Cell(contents).addProperties(ERROR_PROPERTY);
                    cell.addProperties(new HtmlTable.CellProperties().setColSpan(18));
                    feedBlockRow.addCell(cell);
                    table.addRow(feedBlockRow);
                    // Retroactively make the node index cell span 2 rows so it's obvious (hopefully)
                    // what node the feed block state is related to.
                    nodeRow.cells.get(0).addProperties(new HtmlTable.CellProperties().setRowSpan(2));
                }
            }
        }

        private void addRpcAddress(NodeInfo nodeInfo, HtmlTable.Row row) {
            if (nodeInfo.getRpcAddress() == null) {
                row.addCell(new HtmlTable.Cell("-").addProperties(ERROR_PROPERTY));
            } else {
                row.addCell(new HtmlTable.Cell(HtmlTable.escape(nodeInfo.getRpcAddress())));
                if (nodeInfo.isRpcAddressOutdated()) {
                    row.getLastCell().addProperties(WARNING_PROPERTY);
                }
            }
        }

        private void addStartTime(NodeInfo nodeInfo, HtmlTable.Row row) {
            if (nodeInfo.getStartTimestamp() == 0) {
                row.addCell(new HtmlTable.Cell("-").addProperties(ERROR_PROPERTY).addProperties(CENTERED_PROPERTY));
            } else {
                String startTime = RealTimer.printDateNoMilliSeconds(
                        1000 * nodeInfo.getStartTimestamp(), utcTimeZone);
                row.addCell(new HtmlTable.Cell(HtmlTable.escape(startTime)));
            }
        }

        private void addBucketSpacesStats(NodeType nodeType, ClusterStatsAggregator statsAggregator, double minMergeCompletionRatio, NodeInfo nodeInfo, HtmlTable.Row row) {
            if (nodeType.equals(NodeType.STORAGE)) {
                addBucketStats(row, getStatsForContentNode(statsAggregator, nodeInfo, FixedBucketSpaces.defaultSpace()),
                        minMergeCompletionRatio);
                addBucketStats(row, getStatsForContentNode(statsAggregator, nodeInfo, FixedBucketSpaces.globalSpace()),
                        minMergeCompletionRatio);
            } else {
                addBucketStats(row, getStatsForDistributorNode(statsAggregator, nodeInfo, FixedBucketSpaces.defaultSpace()),
                        minMergeCompletionRatio);
                addBucketStats(row, getStatsForDistributorNode(statsAggregator, nodeInfo, FixedBucketSpaces.globalSpace()),
                        minMergeCompletionRatio);
            }
        }

        private void addResourceUsage(NodeInfo nodeInfo, Map<String, Double> feedBlockLimits, HtmlTable.Row row) {
            if (nodeInfo.isDistributor()) {
                row.addCell(new HtmlTable.Cell("-").addProperties(CENTERED_PROPERTY));
                row.addCell(new HtmlTable.Cell("-").addProperties(CENTERED_PROPERTY));
                return;
            }
            addSingleResourceUsageCell(nodeInfo, "disk", feedBlockLimits, row);
            addSingleResourceUsageCell(nodeInfo, "memory", feedBlockLimits, row);
        }

        private void addSingleResourceUsageCell(NodeInfo nodeInfo, String resourceType,
                                                Map<String, Double> feedBlockLimits, HtmlTable.Row row)
        {
            var hostInfo = nodeInfo.getHostInfo();
            var usages = hostInfo.getContentNode().getResourceUsage();

            var usage = usages.get(resourceType);
            if (usage != null && usage.getUsage() != null) {
                row.addCell(new HtmlTable.Cell(String.format("%.2f", usage.getUsage() * 100.0)));
                double limit = feedBlockLimits.getOrDefault(resourceType, 1.0);
                // Mark as error if limit exceeded, warn if within 5% of exceeding
                if (usage.getUsage() > limit) {
                    row.getLastCell().addProperties(ERROR_PROPERTY);
                } else if (usage.getUsage() > (limit - 0.05)) {
                    row.getLastCell().addProperties(WARNING_PROPERTY);
                }
            } else {
                row.addCell(new HtmlTable.Cell("-").addProperties(CENTERED_PROPERTY));
            }
        }

        private void addEventsLastWeek(EventLog eventLog, long currentTime, NodeInfo nodeInfo, HtmlTable.Row row) {
            int nodeEvents = eventLog.getNodeEventsSince(nodeInfo.getNode(),
                    currentTime - eventLog.getRecentTimePeriod());
            row.addCell(new HtmlTable.Cell("" + nodeEvents));
            if (nodeEvents > 20) {
                row.getLastCell().addProperties(ERROR_PROPERTY);
            } else if (nodeEvents > 3) {
                row.getLastCell().addProperties(WARNING_PROPERTY);
            }
        }

        private void addPrematureCrashes(int maxPrematureCrashes, NodeInfo nodeInfo, HtmlTable.Row row) {
            row.addCell(new HtmlTable.Cell("" + nodeInfo.getPrematureCrashCount()));
            if (nodeInfo.getPrematureCrashCount() >= maxPrematureCrashes) {
                row.getLastCell().addProperties(ERROR_PROPERTY);
            } else if (nodeInfo.getPrematureCrashCount() > 0) {
                row.getLastCell().addProperties(WARNING_PROPERTY);
            }
        }

        private void addClusterStateVersion(ClusterStateBundle state, NodeInfo nodeInfo, HtmlTable.Row row) {
            String cellContent = (nodeInfo.getClusterStateVersionActivationAcked() == state.getVersion() || !state.deferredActivation())
                    ? String.format("%d", nodeInfo.getClusterStateVersionBundleAcknowledged())
                    : String.format("%d (%d)", nodeInfo.getClusterStateVersionBundleAcknowledged(),
                                               nodeInfo.getClusterStateVersionActivationAcked());
            row.addCell(new HtmlTable.Cell(cellContent));
            if (nodeInfo.getClusterStateVersionBundleAcknowledged() < state.getVersion() - 2) {
                row.getLastCell().addProperties(ERROR_PROPERTY);
            } else if (nodeInfo.getClusterStateVersionBundleAcknowledged() < state.getVersion()) {
                row.getLastCell().addProperties(WARNING_PROPERTY);
            }
        }

        private void addStatePendingTime(long currentTime, NodeInfo nodeInfo, HtmlTable.Row row) {
            if (nodeInfo.getLatestNodeStateRequestTime() == null) {
                row.addCell(new HtmlTable.Cell("-").addProperties(CENTERED_PROPERTY));
            } else {
                row.addCell(new HtmlTable.Cell(HtmlTable.escape(RealTimer.printDuration(
                        currentTime - nodeInfo.getLatestNodeStateRequestTime()))));
            }
        }

        private void addTimeSinceFirstFailing(NodeInfo nodeInfo, HtmlTable.Row row, long timeSinceContact) {
            row.addCell(new HtmlTable.Cell((timeSinceContact / 1000) + " s"));
            if (timeSinceContact > 60 * 1000) {
                row.getLastCell().addProperties(ERROR_PROPERTY);
            } else if (nodeInfo.getConnectionAttemptCount() > 0) {
                row.getLastCell().addProperties(WARNING_PROPERTY);
            }
        }

        private void addFailedConnectionAttemptCount(NodeInfo nodeInfo, HtmlTable.Row row, long timeSinceContact) {
            row.addCell(new HtmlTable.Cell("" + nodeInfo.getConnectionAttemptCount()));
            if (timeSinceContact > 60 * 1000) {
                row.getLastCell().addProperties(ERROR_PROPERTY);
            } else if (nodeInfo.getConnectionAttemptCount() > 0) {
                row.getLastCell().addProperties(WARNING_PROPERTY);
            }
        }

        private void addBuildTagVersion(String dominantVtag, NodeInfo nodeInfo, HtmlTable.Row row) {
            final String buildTagText =
                    nodeInfo.getVtag() != null
                            ? nodeInfo.getVtag()
                            : TAG_NOT_SET;
            row.addCell(new HtmlTable.Cell(buildTagText));
            if (! dominantVtag.equals(nodeInfo.getVtag())) {
                row.getLastCell().addProperties(WARNING_PROPERTY);
            }
        }

        private void addCurrentState(ClusterState state, NodeInfo nodeInfo, HtmlTable.Row row) {
            NodeState ns = state.getNodeState(nodeInfo.getNode()).clone().setDescription("").setMinUsedBits(16);
            if (state.getClusterState().oneOf("uir")) {
                row.addCell(new HtmlTable.Cell(HtmlTable.escape(ns.toString(true))));
                if (ns.getState().equals(State.DOWN)) {
                    row.getLastCell().addProperties(ERROR_PROPERTY);
                } else if (ns.getState().oneOf("mi")) {
                    row.getLastCell().addProperties(WARNING_PROPERTY);
                }
            } else {
                row.addCell(new HtmlTable.Cell("Cluster " +
                        state.getClusterState().name().toLowerCase()).addProperties(ERROR_PROPERTY));
            }
        }

        private void addWantedState(NodeInfo nodeInfo, HtmlTable.Row row) {
            if (nodeInfo.getWantedState() == null || nodeInfo.getWantedState().getState().equals(State.UP)) {
                row.addCell(new HtmlTable.Cell("-").addProperties(CENTERED_PROPERTY));
            } else {
                row.addCell(new HtmlTable.Cell(HtmlTable.escape(nodeInfo.getWantedState().toString(true))));
                if (nodeInfo.getWantedState().toString(true).indexOf("Disabled by fleet controller") != -1) {
                    row.getLastCell().addProperties(ERROR_PROPERTY);
                } else {
                    row.getLastCell().addProperties(WARNING_PROPERTY);
                }
            }
        }

        private void addReportedState(NodeInfo nodeInfo, HtmlTable.Row row) {
            NodeState reportedState = nodeInfo.getReportedState().clone().setStartTimestamp(0);
            row.addCell(new HtmlTable.Cell(HtmlTable.escape(reportedState.toString(true))));
            if (!nodeInfo.getReportedState().getState().equals(State.UP)) {
                row.getLastCell().addProperties(WARNING_PROPERTY);
            }
        }

        private void addNodeIndex(String pathPrefix, NodeInfo nodeInfo, HtmlTable.Row row) {
            row.addCell(new HtmlTable.Cell("<a href=\"" + pathPrefix + "/node=" + nodeInfo.getNode()
                    + "\">" + nodeInfo.getNodeIndex() + "</a>"));
        }

        private static ContentNodeStats.BucketSpaceStats getStatsForContentNode(ClusterStatsAggregator statsAggregator,
                                                                                NodeInfo nodeInfo,
                                                                                String bucketSpace) {
            ContentNodeStats nodeStats = statsAggregator.getAggregatedStats().getStats().getContentNode(nodeInfo.getNodeIndex());
            if (nodeStats != null) {
                return nodeStats.getBucketSpace(bucketSpace);
            }
            return null;
        }

        private static ContentNodeStats.BucketSpaceStats getStatsForDistributorNode(ClusterStatsAggregator statsAggregator,
                                                                                    NodeInfo nodeInfo,
                                                                                    String bucketSpace) {
            ContentNodeStats nodeStats = statsAggregator.getAggregatedStatsForDistributor(nodeInfo.getNodeIndex());
            return nodeStats.getBucketSpace(bucketSpace);
        }

        private static void addBucketStats(HtmlTable.Row row, ContentNodeStats.BucketSpaceStats bucketSpaceStats,
                                           double minMergeCompletionRatio) {
            if (bucketSpaceStats != null) {
                long bucketsPending = bucketSpaceStats.getBucketsPending();
                long bucketsTotal = bucketSpaceStats.getBucketsTotal();
                String cellValuePending = String.valueOf(bucketsPending);
                String cellValueTotal = String.valueOf(bucketsTotal);
                if (!bucketSpaceStats.valid()) {
                    cellValuePending += "?";
                    cellValueTotal += "?";
                }
                row.addCell(new HtmlTable.Cell(cellValuePending));
                if (bucketSpaceStats.mayHaveBucketsPending(minMergeCompletionRatio)) {
                    row.getLastCell().addProperties(WARNING_PROPERTY);
                }
                row.addCell(new HtmlTable.Cell(cellValueTotal));
            } else {
                row.addCell(new HtmlTable.Cell("-").addProperties(CENTERED_PROPERTY));
                row.addCell(new HtmlTable.Cell("-").addProperties(CENTERED_PROPERTY));
            }
        }

        private void addFooter(final StringBuilder contentBuilder, final long stableStateTimePeriode) {
            contentBuilder.append("<font size=\"-1\">\n")
                    .append("1) FC - Failed connections - We have tried to connect to the nodes this many times " +
                            "without being able to contact it.<br>\n")
                    .append("2) OCT - Out of contact time - Time in seconds we have failed to contact the node.<br>\n")
                    .append("3) SPT - State pending time - Time the current getNodeState request has been " +
                            "pending.<br>\n")
                    .append("4) SSV - System state version - The latest system state version the node has " +
                            "acknowledged (last <em>activated</em> state version in parentheses if this is not equal to SSV).<br>\n")
                    .append("5) PC - Premature crashes - Number of times node has crashed since last time it had " +
                            "been stable in up or down state for more than "
                            + RealTimer.printDuration(stableStateTimePeriode) + ".<br>\n")
                    .append("6) ELW - Events last week - The number of events that has occured on this node the " +
                            "last week. (Or shorter period if a week haven't passed since restart or more than " +
                            "max events to keep in node event log have happened during last week.)<br>\n")
                    .append("</font>\n");
        }
    }

    public Table createNewClusterHtmlTable(final String clusterName, final int slobrokGenerationCount) {
        return new Table(clusterName, slobrokGenerationCount);
    }

}
