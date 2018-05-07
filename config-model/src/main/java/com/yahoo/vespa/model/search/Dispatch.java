// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.vespa.config.search.core.FdispatchrcConfig;
import com.yahoo.vespa.config.search.core.PartitionsConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.content.SearchCoverage;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a dispatch (top-level (tld) or mid-level).
 * There must be one or more tld instances in a search cluster.
 *
 * @author arnej27959
 */
@RestartConfigs({FdispatchrcConfig.class, PartitionsConfig.class})
public class Dispatch extends AbstractService implements SearchInterface,
                                                         FdispatchrcConfig.Producer,
                                                         PartitionsConfig.Producer {

    private static final String TLD_NAME = "tld";
    private static final String DISPATCH_NAME = "dispatch";

    private static final long serialVersionUID = 1L;
    private final DispatchGroup dispatchGroup;
    private final NodeSpec nodeSpec;
    private final int dispatchLevel;
    private final boolean preferLocalRow;
    private final boolean isTopLevel;

    private Dispatch(DispatchGroup dispatchGroup, AbstractConfigProducer parent, String subConfigId,
                     NodeSpec nodeSpec, int dispatchLevel, boolean preferLocalRow, boolean isTopLevel) {
        super(parent, subConfigId);
        this.dispatchGroup = dispatchGroup;
        this.nodeSpec = nodeSpec;
        this.dispatchLevel = dispatchLevel;
        this.preferLocalRow = preferLocalRow;
        this.isTopLevel = isTopLevel;
        portsMeta.on(0).tag("rpc").tag("admin");
        portsMeta.on(1).tag("fs4");
        portsMeta.on(2).tag("http").tag("json").tag("health").tag("state");
        setProp("clustertype", "search")
                .setProp("clustername", dispatchGroup.getClusterName())
                .setProp("index", nodeSpec.groupIndex());
    }

    public static Dispatch createTld(DispatchGroup dispatchGroup, AbstractConfigProducer parent, int rowId) {
        return createTld(dispatchGroup, parent, rowId, false);
    }

    public static Dispatch createTld(DispatchGroup dispatchGroup, AbstractConfigProducer parent, int rowId, boolean preferLocalRow) {
        String subConfigId = TLD_NAME + "." + rowId;
        return new Dispatch(dispatchGroup, parent, subConfigId, new NodeSpec(rowId, 0), 0, preferLocalRow, true);
    }

    public static Dispatch createTldWithContainerIdInName(DispatchGroup dispatchGroup, AbstractConfigProducer parent, String containerName, int containerIndex) {
        String subConfigId = containerName + "." + containerIndex + "." + TLD_NAME + "." + containerIndex;
        return new Dispatch(dispatchGroup, parent, subConfigId, new NodeSpec(containerIndex, 0), 0, false, true);
    }

    public static Dispatch createDispatchWithStableConfigId(DispatchGroup dispatchGroup, AbstractConfigProducer parent, NodeSpec nodeSpec, int distributionKey, int dispatchLevel) {
        String subConfigId = DISPATCH_NAME + "." + distributionKey;
        return new Dispatch(dispatchGroup, parent, subConfigId, nodeSpec, dispatchLevel, false, false);
    }

    /**
     * Override the default service-type
     * @return String "topleveldispatch"
     */
    public String getServiceType() {
        return "topleveldispatch";
    }

    /**
     * @return the startup command
     */
    public String getStartupCommand() {
        return "exec sbin/vespa-dispatch -c $VESPA_CONFIG_ID";
    }

    public int getFrtPort()  { return getRelativePort(0); }
    public int getDispatchPort()   { return getRelativePort(1); }
    @Override
    public int getHealthPort() { return getRelativePort(2); }

    /**
     * Twice the default of the number of threads in the container.
     * Could have been unbounded if it was not roundrobin, but stack based usage in dispatch.
     * We are not putting to much magic into this one as this will disappear as soon as
     * dispatch is implemented in Java.
     */
    public int getMaxThreads() { return 500*2; }

    public String getHostname() {
        return getHost().getHostname();
    }

    @Override
    public NodeSpec getNodeSpec() {
        return nodeSpec;
    }

    public String getDispatcherConnectSpec() {
        return "tcp/" + getHost().getHostname() + ":" + getDispatchPort();
    }

    public DispatchGroup getDispatchGroup() {
        return dispatchGroup;
    }

    @Override
    public void getConfig(FdispatchrcConfig.Builder builder) {
        builder.ptport(getDispatchPort()).
                frtport(getFrtPort()).
                healthport(getHealthPort()).
                maxthreads(getMaxThreads());
        if (!isTopLevel) {
            builder.partition(getNodeSpec().partitionId());
            builder.dispatchlevel(dispatchLevel);
        }
    }

    @Override
    public void getConfig(PartitionsConfig.Builder builder) {
        int rowbits = dispatchGroup.getRowBits();
        final PartitionsConfig.Dataset.Builder datasetBuilder = new PartitionsConfig.Dataset.Builder().
                id(0).
                searchablecopies(dispatchGroup.getSearchableCopies()).
                refcost(1).
                rowbits(rowbits).
                numparts(dispatchGroup.getNumPartitions()).
                mpp(dispatchGroup.getMinNodesPerColumn());
        if (dispatchGroup.useFixedRowInDispatch()) {
            datasetBuilder.querydistribution(PartitionsConfig.Dataset.Querydistribution.Enum.FIXEDROW);
            datasetBuilder.maxnodesdownperfixedrow(dispatchGroup.getMaxNodesDownPerFixedRow());
        }
        SearchCoverage coverage = dispatchGroup.getSearchCoverage();
        if (coverage != null) {
            if (coverage.getMinimum() != null) {
                datasetBuilder.minimal_searchcoverage(coverage.getMinimum() * 100); // as percentage
            }
            if (coverage.getMinWaitAfterCoverageFactor() != null) {
                datasetBuilder.higher_coverage_minsearchwait(coverage.getMinWaitAfterCoverageFactor());
            }
            if (coverage.getMaxWaitAfterCoverageFactor() != null) {
                datasetBuilder.higher_coverage_maxsearchwait(coverage.getMaxWaitAfterCoverageFactor());
            }
        }

        Tuning tuning = dispatchGroup.getTuning();
        boolean useLocalNode = false;
        if (tuning != null && tuning.dispatch != null) {
            useLocalNode = tuning.dispatch.useLocalNode;
        }
        final List<PartitionsConfig.Dataset.Engine.Builder> allEngines = new ArrayList<>();
        for (SearchInterface searchNode : dispatchGroup.getSearchersIterable()) {
            final PartitionsConfig.Dataset.Engine.Builder engineBuilder = new PartitionsConfig.Dataset.Engine.Builder().
                    name_and_port(searchNode.getDispatcherConnectSpec()).
                    rowid(searchNode.getNodeSpec().groupIndex()).
                    partid(searchNode.getNodeSpec().partitionId());
            allEngines.add(engineBuilder);
            if (preferLocalRow) {
                if (getHostname().equals(searchNode.getHostName())) {
                    engineBuilder.refcost(1);
                } else {
                    engineBuilder.refcost(Integer.MAX_VALUE);
                }
            }

            if (!useLocalNode || getHostname().equals(searchNode.getHostName())) {
                if (useLocalNode) {
                    engineBuilder.rowid(0);
                }
                datasetBuilder.engine.add(engineBuilder);

            }
        }
        //Do not create empty engine list for a dataset if no local search nodes found
        if(datasetBuilder.engine.isEmpty() && useLocalNode) {
          for(PartitionsConfig.Dataset.Engine.Builder engineBuilder: allEngines) {
            datasetBuilder.engine.add(engineBuilder);
          }
        }

        builder.dataset.add(datasetBuilder);

        if (tuning != null) {
            tuning.getConfig(builder);
            scaleMaxHitsPerPartitions(builder, tuning);
        }
    }

    private int getNumLeafNodesInGroup() {
        int numSearchers = 0;
        for (SearchInterface search : dispatchGroup.getSearchersIterable()) {
            if (search instanceof Dispatch) {
                numSearchers += ((Dispatch) search).getNumLeafNodesInGroup();
            } else {
                numSearchers++;
            }
        }
        if (numSearchers > 0) {
            // Divide by number of partitions, otherwise we would count the same leaf node partition number of times.
            return numSearchers / dispatchGroup.getNumPartitions();
        }
        return 0;
    }

    private void scaleMaxHitsPerPartitions(PartitionsConfig.Builder builder, Tuning tuning) {
        if (tuning == null || tuning.dispatch == null || tuning.dispatch.maxHitsPerPartition == null) {
            return;
        }
        int numLeafNodes = getNumLeafNodesInGroup();
        for (PartitionsConfig.Dataset.Builder dataset : builder.dataset) {
            dataset.maxhitspernode(tuning.dispatch.maxHitsPerPartition * numLeafNodes);
        }
    }

    /**
     * @return the number of ports needed
     */
    public int getPortCount() { return 3; }
}
