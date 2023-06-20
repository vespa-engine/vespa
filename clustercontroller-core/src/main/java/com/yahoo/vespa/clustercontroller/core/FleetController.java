// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.database.ZooKeeperDatabaseFactory;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeListener;
import com.yahoo.vespa.clustercontroller.core.listeners.SlobrokListener;
import com.yahoo.vespa.clustercontroller.core.listeners.SystemStateListener;
import com.yahoo.vespa.clustercontroller.core.rpc.RPCCommunicator;
import com.yahoo.vespa.clustercontroller.core.rpc.RpcServer;
import com.yahoo.vespa.clustercontroller.core.rpc.SlobrokClient;
import com.yahoo.vespa.clustercontroller.core.status.ClusterStateRequestHandler;
import com.yahoo.vespa.clustercontroller.core.status.LegacyIndexPageRequestHandler;
import com.yahoo.vespa.clustercontroller.core.status.LegacyNodePageRequestHandler;
import com.yahoo.vespa.clustercontroller.core.status.NodeHealthRequestHandler;
import com.yahoo.vespa.clustercontroller.core.status.StatusHandler;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;
import com.yahoo.vespa.clustercontroller.utils.util.MetricReporter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FleetController implements NodeListener, SlobrokListener, SystemStateListener,
                                        Runnable, RemoteClusterControllerTaskScheduler {

    private static final Logger logger = Logger.getLogger(FleetController.class.getName());

    private final FleetControllerContext context;
    private final Timer timer;
    private final Object monitor;
    private final EventLog eventLog;
    private final NodeLookup nodeLookup;
    private final ContentCluster cluster;
    private final Communicator communicator;
    private final NodeStateGatherer stateGatherer;
    private final StateChangeHandler stateChangeHandler;
    private final SystemStateBroadcaster systemStateBroadcaster;
    private final StateVersionTracker stateVersionTracker;
    private final StatusHandler.ContainerStatusPageServer statusPageServer;
    private final RpcServer rpcServer;
    private final DatabaseHandler database;
    private final MasterElectionHandler masterElectionHandler;
    private Thread runner = null;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private FleetControllerOptions options;
    private FleetControllerOptions nextOptions;
    private final List<SystemStateListener> systemStateListeners = new CopyOnWriteArrayList<>();
    private boolean processingCycle = false;
    private boolean wantedStateChanged = false;
    private long cycleCount = 0;
    private long lastMetricUpdateCycleCount = 0;
    private long nextStateSendTime = 0;
    private Long controllerThreadId = null;

    private boolean waitingForCycle = false;
    private final StatusPageServer.PatternRequestRouter statusRequestRouter = new StatusPageServer.PatternRequestRouter();
    private final List<ClusterStateBundle> newStates = new ArrayList<>();
    private final List<ClusterStateBundle> convergedStates = new ArrayList<>();
    private final Queue<RemoteClusterControllerTask> remoteTasks = new LinkedList<>();
    private final MetricUpdater metricUpdater;
    private final LegacyIndexPageRequestHandler indexPageRequestHandler;

    private boolean isMaster = false;
    private boolean inMasterMoratorium = false;
    private boolean isStateGatherer = false;
    private long firstAllowedStateBroadcast = Long.MAX_VALUE;
    private long tickStartTime = Long.MAX_VALUE;

    private final List<RemoteClusterControllerTask> tasksPendingStateRecompute = new ArrayList<>();
    // Invariant: queued task versions are monotonically increasing with queue position
    private final Queue<VersionDependentTaskCompletion> taskCompletionQueue = new ArrayDeque<>();

    // Legacy behavior is an empty set of explicitly configured bucket spaces, which means that
    // only a baseline cluster state will be sent from the controller and no per-space state
    // deriving is done.
    private final Set<String> configuredBucketSpaces = Set.of(FixedBucketSpaces.defaultSpace(), FixedBucketSpaces.globalSpace());

    public FleetController(FleetControllerContext context,
                           Timer timer,
                           EventLog eventLog,
                           ContentCluster cluster,
                           NodeStateGatherer nodeStateGatherer,
                           Communicator communicator,
                           RpcServer server,
                           NodeLookup nodeLookup,
                           DatabaseHandler database,
                           StateChangeHandler stateChangeHandler,
                           SystemStateBroadcaster systemStateBroadcaster,
                           MasterElectionHandler masterElectionHandler,
                           MetricUpdater metricUpdater,
                           FleetControllerOptions options) {
        context.log(logger, Level.FINE, "Created");
        this.context = context;
        this.timer = timer;
        this.monitor = timer;
        this.eventLog = eventLog;
        this.options = options;
        this.nodeLookup = nodeLookup;
        this.cluster = cluster;
        this.communicator = communicator;
        this.database = database;
        this.stateGatherer = nodeStateGatherer;
        this.stateChangeHandler = stateChangeHandler;
        this.systemStateBroadcaster = systemStateBroadcaster;
        this.stateVersionTracker = new StateVersionTracker(options.minMergeCompletionRatio());
        this.metricUpdater = metricUpdater;
        this.statusPageServer = new StatusHandler.ContainerStatusPageServer();
        this.rpcServer = server;
        this.masterElectionHandler = masterElectionHandler;
        this.statusRequestRouter.addHandler(new LegacyNodePageRequestHandler(timer, eventLog, cluster));
        this.statusRequestRouter.addHandler(new NodeHealthRequestHandler());
        this.statusRequestRouter.addHandler(new ClusterStateRequestHandler(stateVersionTracker));
        this.indexPageRequestHandler = new LegacyIndexPageRequestHandler(timer, cluster, masterElectionHandler, stateVersionTracker, eventLog, options);
        this.statusRequestRouter.addHandler(indexPageRequestHandler);

        propagateOptions();
    }

    public static FleetController create(FleetControllerOptions options, MetricReporter metricReporter) throws Exception {
        var context = new FleetControllerContextImpl(options);
        var timer = new RealTimer();
        var metricUpdater = new MetricUpdater(metricReporter, options.fleetControllerIndex(), options.clusterName());
        var log = new EventLog(timer, metricUpdater);
        var cluster = new ContentCluster(options);
        var stateGatherer = new NodeStateGatherer(timer, timer, log);
        var communicator = new RPCCommunicator(
                RPCCommunicator.createRealSupervisor(),
                timer,
                options.fleetControllerIndex(),
                options.nodeStateRequestTimeoutMS(),
                options.nodeStateRequestTimeoutEarliestPercentage(),
                options.nodeStateRequestTimeoutLatestPercentage(),
                options.nodeStateRequestRoundTripTimeMaxSeconds());
        var database = new DatabaseHandler(context, new ZooKeeperDatabaseFactory(context), timer, options.zooKeeperServerAddress(), timer);
        var lookUp = new SlobrokClient(context, timer, options.slobrokConnectionSpecs());
        var stateGenerator = new StateChangeHandler(context, timer, log);
        var stateBroadcaster = new SystemStateBroadcaster(context, timer, timer);
        var masterElectionHandler = new MasterElectionHandler(context, options.fleetControllerIndex(), options.fleetControllerCount(), timer, timer);
        var controller = new FleetController(context, timer, log, cluster, stateGatherer, communicator,
                                             null, lookUp, database, stateGenerator,
                                             stateBroadcaster, masterElectionHandler, metricUpdater, options);
        controller.start();
        return controller;
    }

    public void start() {
        runner = new Thread(this);
        runner.start();
    }

    public Object getMonitor() { return monitor; }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isMaster() {
        synchronized (monitor) {
            return isMaster;
        }
    }

    public ClusterState getClusterState() {
        synchronized (monitor) {
            return systemStateBroadcaster.getClusterState();
        }
    }

    public ClusterStateBundle getClusterStateBundle() {
        synchronized (monitor) {
            return systemStateBroadcaster.getClusterStateBundle();
        }
    }

    public void schedule(RemoteClusterControllerTask task) {
        synchronized (monitor) {
            context.log(logger, Level.FINE, "Scheduled remote task " + task.getClass().getName() + " for execution");
            remoteTasks.add(task);
        }
    }

    /** Used for unit testing. */
    public void addSystemStateListener(SystemStateListener listener) {
        systemStateListeners.add(listener);
        // Always give cluster state listeners the current state, in case acceptable state has come before listener is registered.
        var state = getSystemState();
        listener.handleNewPublishedState(ClusterStateBundle.ofBaselineOnly(AnnotatedClusterState.withoutAnnotations(state)));
        ClusterStateBundle convergedState = systemStateBroadcaster.getLastClusterStateBundleConverged();
        if (convergedState != null)
            listener.handleStateConvergedInCluster(convergedState);
    }

    public FleetControllerOptions getOptions() {
        synchronized(monitor) {
            return FleetControllerOptions.Builder.copy(options).build();
        }
    }

    public NodeState getReportedNodeState(Node n) {
        synchronized(monitor) {
            NodeInfo node = cluster.getNodeInfo(n);
            if (node == null) {
                throw new IllegalStateException("Did not find node " + n + " in cluster " + cluster);
            }
            return node.getReportedState();
        }
    }

    // Only used in tests
    public NodeState getWantedNodeState(Node n) {
        synchronized(monitor) {
            return cluster.getNodeInfo(n).getWantedState();
        }
    }

    public com.yahoo.vdslib.state.ClusterState getSystemState() {
        synchronized(monitor) {
            return stateVersionTracker.getVersionedClusterState();
        }
    }

    public int getRpcPort() { return rpcServer.getPort(); }

    public void shutdown() throws InterruptedException {
        if (runner != null && isRunning()) {
            context.log(logger, Level.INFO, "Joining event thread.");
            running.set(false);
            synchronized(monitor) { monitor.notifyAll(); }
            runner.join();
        }
        context.log(logger, Level.INFO, "FleetController done shutting down event thread.");
        controllerThreadId = Thread.currentThread().getId();
        database.shutdown(databaseContext);

        statusPageServer.shutdown();
        if (rpcServer != null) {
            rpcServer.shutdown();
        }
        communicator.shutdown();
        nodeLookup.shutdown();
    }

    public void updateOptions(FleetControllerOptions options) {
        var newId = FleetControllerId.fromOptions(options);
        synchronized(monitor) {
            assert newId.equals(context.id());
            context.log(logger, Level.INFO, "FleetController has new options");
            nextOptions = FleetControllerOptions.Builder.copy(options).build();
            monitor.notifyAll();
        }
    }

    private void verifyInControllerThread() {
        if (controllerThreadId != null && controllerThreadId != Thread.currentThread().getId()) {
            throw new IllegalStateException("Function called from non-controller thread. Shouldn't happen.");
        }
    }

    private ClusterState latestCandidateClusterState() {
        return stateVersionTracker.getLatestCandidateState().getClusterState();
    }

    @Override
    public void handleNewNodeState(NodeInfo node, NodeState newState) {
        verifyInControllerThread();
        stateChangeHandler.handleNewReportedNodeState(latestCandidateClusterState(), node, newState, this);
    }

    @Override
    public void handleNewWantedNodeState(NodeInfo node, NodeState newState) {
        verifyInControllerThread();
        wantedStateChanged = true;
        stateChangeHandler.proposeNewNodeState(stateVersionTracker.getVersionedClusterState(), node, newState);
    }

    @Override
    public void handleRemovedNode(Node node) {
        verifyInControllerThread();
        // Prune orphaned wanted states
        wantedStateChanged = true;
    }

    @Override
    public void handleUpdatedHostInfo(NodeInfo nodeInfo, HostInfo newHostInfo) {
        verifyInControllerThread();
        triggerBundleRecomputationIfResourceExhaustionStateChanged(nodeInfo, newHostInfo);
        stateVersionTracker.handleUpdatedHostInfo(nodeInfo, newHostInfo);
    }

    private void triggerBundleRecomputationIfResourceExhaustionStateChanged(NodeInfo nodeInfo, HostInfo newHostInfo) {
        if (!options.clusterFeedBlockEnabled()) {
            return;
        }
        var calc = createResourceExhaustionCalculator();
        // Important: nodeInfo contains the _current_ host info _prior_ to newHostInfo being applied.
        var previouslyExhausted = calc.enumerateNodeResourceExhaustions(nodeInfo);
        var nowExhausted        = calc.resourceExhaustionsFromHostInfo(nodeInfo, newHostInfo);
        if (!previouslyExhausted.equals(nowExhausted)) {
            context.log(logger, Level.FINE, () -> String.format("Triggering state recomputation due to change in cluster feed block: %s -> %s",
                                            previouslyExhausted, nowExhausted));
            stateChangeHandler.setStateChangedFlag();
        }
    }

    @Override
    public void handleNewNode(NodeInfo node) {
        verifyInControllerThread();
        stateChangeHandler.handleNewNode(node);
    }
    @Override
    public void handleMissingNode(NodeInfo node) {
        verifyInControllerThread();
        stateChangeHandler.handleMissingNode(stateVersionTracker.getVersionedClusterState(), node, this);
    }
    @Override
    public void handleNewRpcAddress(NodeInfo node) {
        verifyInControllerThread();
        stateChangeHandler.handleNewRpcAddress(node);
    }
    @Override
    public void handleReturnedRpcAddress(NodeInfo node) {
        verifyInControllerThread();
        stateChangeHandler.handleReturnedRpcAddress(node);
    }

    @Override
    public void handleNewPublishedState(ClusterStateBundle stateBundle) {
        verifyInControllerThread();
        ClusterState baselineState = stateBundle.getBaselineClusterState();
        newStates.add(stateBundle);
        metricUpdater.updateClusterStateMetrics(cluster, baselineState,
                ResourceUsageStats.calculateFrom(cluster.getNodeInfos(), options.clusterFeedBlockLimit(), stateBundle.getFeedBlock()));
        lastMetricUpdateCycleCount = cycleCount;
        systemStateBroadcaster.handleNewClusterStates(stateBundle);
        // Iff master, always store new version in ZooKeeper _before_ publishing to any
        // nodes so that a cluster controller crash after publishing but before a successful
        // ZK store will not risk reusing the same version number.
        // Use isMaster instead of election handler state, as isMaster is set _after_ we have
        // completed a leadership event edge, so we know we have read from ZooKeeper.
        if (isMaster) {
            storeClusterStateMetaDataToZooKeeper(stateBundle);
        }
    }

    public EventLog getEventLog() { return eventLog; }

    private boolean maybePublishOldMetrics() {
        verifyInControllerThread();
        if (isMaster() && cycleCount > 300 + lastMetricUpdateCycleCount) {
            ClusterStateBundle stateBundle = stateVersionTracker.getVersionedClusterStateBundle();
            ClusterState baselineState = stateBundle.getBaselineClusterState();
            metricUpdater.updateClusterStateMetrics(cluster, baselineState,
                    ResourceUsageStats.calculateFrom(cluster.getNodeInfos(), options.clusterFeedBlockLimit(), stateBundle.getFeedBlock()));
            lastMetricUpdateCycleCount = cycleCount;
            return true;
        } else {
            return false;
        }
    }

    private void storeClusterStateMetaDataToZooKeeper(ClusterStateBundle stateBundle) {
        database.saveLatestSystemStateVersion(databaseContext, stateBundle.getVersion());
        database.saveLatestClusterStateBundle(databaseContext, stateBundle);
    }

    /**
     * This function gives data of the current state in master election.
     * The keys in the given map are indices of fleet controllers.
     * The values are what fleetcontroller that fleetcontroller wants to
     * become master.
     * <p>
     * If more than half the fleetcontrollers want a node to be master and
     * that node also wants itself as master, that node is the single master.
     * If this condition is not met, there is currently no master.
     */
    public void handleFleetData(Map<Integer, Integer> data) {
        verifyInControllerThread();
        context.log(logger, Level.FINEST, "Sending fleet data event on to master election handler");
        metricUpdater.updateMasterElectionMetrics(data);
        masterElectionHandler.handleFleetData(data);
    }

    /**
     * Called when we can no longer contact database.
     */
    public void lostDatabaseConnection() {
        verifyInControllerThread();
        boolean wasMaster = isMaster;
        masterElectionHandler.lostDatabaseConnection();
        if (wasMaster) {
            // Enforce that we re-fetch all state information from ZooKeeper upon the next tick if we're still master.
            dropLeadershipState();
            metricUpdater.updateMasterState(false);
        }
    }

    private void failAllVersionDependentTasks() {
        tasksPendingStateRecompute.forEach(task -> {
            task.handleFailure(RemoteClusterControllerTask.Failure.of(
                    RemoteClusterControllerTask.FailureCondition.LEADERSHIP_LOST));
            task.notifyCompleted();
        });
        tasksPendingStateRecompute.clear();
        taskCompletionQueue.forEach(task -> {
            task.getTask().handleFailure(RemoteClusterControllerTask.Failure.of(
                    RemoteClusterControllerTask.FailureCondition.LEADERSHIP_LOST));
            task.getTask().notifyCompleted();
        });
        taskCompletionQueue.clear();
    }

    /** Called when all distributors have acked newest cluster state version. */
    public void handleAllDistributorsInSync(DatabaseHandler database, DatabaseHandler.DatabaseContext dbContext) {
        Set<ConfiguredNode> nodes = new HashSet<>(cluster.clusterInfo().getConfiguredNodes().values());
        // TODO wouldn't it be better to always get bundle information from the state broadcaster?
        var currentBundle = stateVersionTracker.getVersionedClusterStateBundle();
        context.log(logger, Level.FINE, () -> String.format("All distributors have ACKed cluster state version %d", currentBundle.getVersion()));
        stateChangeHandler.handleAllDistributorsInSync(currentBundle.getBaselineClusterState(), nodes, database, dbContext);
        convergedStates.add(currentBundle);
    }

    private boolean changesConfiguredNodeSet(Collection<ConfiguredNode> newNodes) {
        if (newNodes.size() != cluster.getConfiguredNodes().size()) return true;
        if (! cluster.getConfiguredNodes().values().containsAll(newNodes)) return true;

        // Check retirement changes
        for (ConfiguredNode node : newNodes) {
            if (node.retired() != cluster.getConfiguredNodes().get(node.index()).retired()) {
                return true;
            }
        }

        return false;
    }

    /** This is called when the options field has been set to a new set of options */
    private void propagateOptions() {
        verifyInControllerThread();
        selfTerminateIfConfiguredNodeIndexHasChanged();

        if (changesConfiguredNodeSet(options.nodes())) {
            // Force slobrok node re-fetch in case of changes to the set of configured nodes
            cluster.setSlobrokGenerationCount(0);
        }

        stateVersionTracker.setMinMergeCompletionRatio(options.minMergeCompletionRatio());

        communicator.propagateOptions(options);
        indexPageRequestHandler.propagateOptions(options);

        if (nodeLookup instanceof SlobrokClient) {
            ((SlobrokClient) nodeLookup).setSlobrokConnectionSpecs(options.slobrokConnectionSpecs());
        }
        eventLog.setMaxSize(options.eventLogMaxSize(), options.eventNodeLogMaxSize());
        cluster.setDistribution(options.storageDistribution());
        cluster.setNodes(options.nodes(), databaseContext.getNodeStateUpdateListener());
        database.setZooKeeperAddress(options.zooKeeperServerAddress(), databaseContext);
        database.setZooKeeperSessionTimeout(options.zooKeeperSessionTimeout(), databaseContext);
        stateGatherer.setMaxSlobrokDisconnectGracePeriod(options.maxSlobrokDisconnectGracePeriod());
        stateGatherer.setNodeStateRequestTimeout(options.nodeStateRequestTimeoutMS());

        // TODO: remove as many temporal parameter dependencies as possible here. Currently duplication of state.
        stateChangeHandler.reconfigureFromOptions(options);

        masterElectionHandler.setFleetControllerCount(options.fleetControllerCount());
        masterElectionHandler.setMasterZooKeeperCooldownPeriod(options.masterZooKeeperCooldownPeriod());

        if (rpcServer != null) {
            rpcServer.setMasterElectionHandler(masterElectionHandler);
            rpcServer.setSlobrokConnectionSpecs(options.slobrokConnectionSpecs(), options.rpcPort());
        }

        long currentTime = timer.getCurrentTimeInMillis();
        nextStateSendTime = Math.min(currentTime + options.minTimeBetweenNewSystemStates(), nextStateSendTime);
    }

    private void selfTerminateIfConfiguredNodeIndexHasChanged() {
        var newId = new FleetControllerId(options.clusterName(), options.fleetControllerIndex());
        if (!newId.equals(context.id())) {
            context.log(logger, Level.WARNING, context.id() + " got new configuration for " + newId + ". We do not support doing this live; " +
                               "immediately exiting now to force new configuration");
            prepareShutdownEdge();
            System.exit(1);
        }
    }

    public void tick() throws Exception {
        synchronized (monitor) {
            boolean didWork;
            didWork = metricUpdater.forWork("doNextZooKeeperTask", () -> database.doNextZooKeeperTask(databaseContext));
            didWork |= metricUpdater.forWork("updateMasterElectionState", this::updateMasterElectionState);
            didWork |= metricUpdater.forWork("handleLeadershipEdgeTransitions", this::handleLeadershipEdgeTransitions);
            stateChangeHandler.setMaster(isMaster);

            if ( ! isRunning()) { return; }
            // Process zero or more getNodeState responses that we have received.
            didWork |= metricUpdater.forWork("stateGatherer-processResponses", () -> stateGatherer.processResponses(this));

            if ( ! isRunning()) { return; }

            if (masterElectionHandler.isFirstInLine()) {
                didWork |= resyncLocallyCachedState(); // Calls to metricUpdate.forWork inside method
            } else {
                stepDownAsStateGatherer();
            }

            if ( ! isRunning()) { return; }
            didWork |= metricUpdater.forWork("systemStateBroadcaster-processResponses", systemStateBroadcaster::processResponses);
            if ( ! isRunning()) { return; }
            if (isMaster) {
                didWork |= metricUpdater.forWork("broadcastClusterStateToEligibleNodes", this::broadcastClusterStateToEligibleNodes);
                systemStateBroadcaster.checkIfClusterStateIsAckedByAllDistributors(database, databaseContext, this);
            }

            if ( ! isRunning()) { return; }
            didWork |= metricUpdater.forWork("processAnyPendingStatusPageRequest", this::processAnyPendingStatusPageRequest);
            if ( ! isRunning()) { return; }
            if (rpcServer != null) {
                didWork |= metricUpdater.forWork("handleRpcRequests", () -> rpcServer.handleRpcRequests(cluster, consolidatedClusterState(), this));
            }

            if ( ! isRunning()) { return; }
            didWork |= metricUpdater.forWork("processNextQueuedRemoteTask", this::processNextQueuedRemoteTask);
            didWork |= metricUpdater.forWork("completeSatisfiedVersionDependentTasks", this::completeSatisfiedVersionDependentTasks);
            didWork |= metricUpdater.forWork("maybePublishOldMetrics", this::maybePublishOldMetrics);

            processingCycle = false;
            ++cycleCount;
            long tickStopTime = timer.getCurrentTimeInMillis();
            if (tickStopTime >= tickStartTime) {
                metricUpdater.addTickTime(tickStopTime - tickStartTime, didWork);
            }
            // Always sleep some to avoid using too much CPU and avoid starving waiting threads
            monitor.wait(didWork || waitingForCycle ? 1 : options.cycleWaitTime());
            if ( ! isRunning()) { return; }
            tickStartTime = timer.getCurrentTimeInMillis();
            processingCycle = true;
            if (nextOptions != null) { // if reconfiguration has given us new options, propagate them
                switchToNewConfig();
            }
        }
        if (isRunning()) {
            propagateNewStatesToListeners();
        }
    }

    private boolean updateMasterElectionState() {
        try {
            return masterElectionHandler.watchMasterElection(database, databaseContext);
        } catch (Exception e) {
            context.log(logger, Level.WARNING, "Failed to watch master election: " + e);
        }
        return false;
    }

    private void stepDownAsStateGatherer() {
        if (isStateGatherer) {
            cluster.clearStates(); // Remove old states that we are no longer certain of as we stop gathering information
            eventLog.add(new ClusterEvent(ClusterEvent.Type.MASTER_ELECTION, "This node is no longer a node state gatherer.", timer.getCurrentTimeInMillis()));
        }
        isStateGatherer = false;
    }

    private void switchToNewConfig() {
        options = nextOptions;
        nextOptions = null;
        try {
            propagateOptions();
        } catch (Exception e) {
            context.log(logger, Level.SEVERE, "Failed to handle new fleet controller config", e);
        }
    }

    private boolean processAnyPendingStatusPageRequest() {
        StatusPageServer.HttpRequest statusRequest = statusPageServer.getCurrentHttpRequest();
        if (statusRequest != null) {
            verifyInControllerThread();
            statusPageServer.fetchStatusPage(statusRequest, statusRequestRouter, timer);
            return true;
        }
        return false;
    }

    private boolean broadcastClusterStateToEligibleNodes() {
        // If there's a pending DB store we have not yet been able to store the
        // current state bundle to ZK and must therefore _not_ allow it to be published.
        if (database.hasPendingClusterStateMetaDataStore()) {
            context.log(logger, Level.FINE, "Can't publish current cluster state as it has one or more pending ZooKeeper stores");
            return false;
        }
        boolean sentAny = false;
        // Give nodes a fair chance to respond first time to state gathering requests, so we don't
        // disturb system when we take over. Allow anyway if we have states from all nodes.
        long currentTime = timer.getCurrentTimeInMillis();
        if ((currentTime >= firstAllowedStateBroadcast || cluster.allStatesReported())
            && currentTime >= nextStateSendTime)
        {
            if (inMasterMoratorium) {
                context.log(logger, Level.INFO, currentTime < firstAllowedStateBroadcast ?
                         "Master moratorium complete: all nodes have reported in" :
                         "Master moratorium complete: timed out waiting for all nodes to report in");
                // Reset firstAllowedStateBroadcast to make sure all future times are after firstAllowedStateBroadcast
                firstAllowedStateBroadcast = currentTime;
                inMasterMoratorium = false;
            }
            sentAny = systemStateBroadcaster.broadcastNewStateBundleIfRequired(
                    databaseContext, communicator, database.getLastKnownStateBundleVersionWrittenBySelf());
            if (sentAny) {
                // FIXME won't this inhibit resending to unresponsive nodes?
                nextStateSendTime = currentTime + options.minTimeBetweenNewSystemStates();
            }
        }
        // Always allow activations if we've already broadcasted a state
        sentAny |= systemStateBroadcaster.broadcastStateActivationsIfRequired(databaseContext, communicator);
        return sentAny;
    }

    private void propagateNewStatesToListeners() {
        if ( ! newStates.isEmpty()) {
            synchronized (systemStateListeners) {
                for (ClusterStateBundle stateBundle : newStates) {
                    for (SystemStateListener listener : systemStateListeners) {
                        listener.handleNewPublishedState(stateBundle);
                    }
                }
                newStates.clear();
            }
        }
        if ( ! convergedStates.isEmpty()) {
            synchronized (systemStateListeners) {
                for (ClusterStateBundle stateBundle : convergedStates) {
                    for (SystemStateListener listener : systemStateListeners) {
                        listener.handleStateConvergedInCluster(stateBundle);
                    }
                }
                convergedStates.clear();
            }
        }
    }

    private boolean processNextQueuedRemoteTask() {
        metricUpdater.updateRemoteTaskQueueSize(remoteTasks.size());

        RemoteClusterControllerTask task = remoteTasks.poll();
        if (task == null) {
            return false;
        }

        final RemoteClusterControllerTask.Context taskContext = createRemoteTaskProcessingContext();
        context.log(logger, Level.FINEST, () -> String.format("Processing remote task of type '%s'", task.getClass().getName()));
        task.doRemoteFleetControllerTask(taskContext);
        if (taskMayBeCompletedImmediately(task)) {
            context.log(logger, Level.FINEST, () -> String.format("Done processing remote task of type '%s'", task.getClass().getName()));
            task.notifyCompleted();
        } else {
            context.log(logger, Level.FINEST, () -> String.format("Remote task of type '%s' queued until state recomputation", task.getClass().getName()));
            tasksPendingStateRecompute.add(task);
        }

        return true;
    }

    private boolean taskMayBeCompletedImmediately(RemoteClusterControllerTask task) {
        // We cannot introduce a version barrier for tasks when we're not the master (and therefore will not publish new versions).
        return (!task.hasVersionAckDependency() || task.isFailed() || !isMaster);
    }

    private RemoteClusterControllerTask.Context createRemoteTaskProcessingContext() {
        final RemoteClusterControllerTask.Context context = new RemoteClusterControllerTask.Context();
        context.cluster = cluster;
        context.currentConsolidatedState = consolidatedClusterState();
        context.publishedClusterStateBundle = stateVersionTracker.getVersionedClusterStateBundle();
        context.masterInfo = new MasterInterface() {
            @Override public boolean isMaster() { return isMaster; }
            @Override public Integer getMaster() { return masterElectionHandler.getMaster(); }
            @Override public boolean inMasterMoratorium() { return inMasterMoratorium; }
        };

        context.nodeListener = this;
        context.slobrokListener = this;
        return context;
    }

    private static long effectiveActivatedStateVersion(NodeInfo nodeInfo, ClusterStateBundle bundle) {
        return bundle.deferredActivation()
                ? nodeInfo.getClusterStateVersionActivationAcked()
                : nodeInfo.getClusterStateVersionBundleAcknowledged();
    }

    private List<Node> enumerateNodesNotYetAckedAtLeastVersion(long version) {
        var bundle = systemStateBroadcaster.getClusterStateBundle();
        if (bundle == null) {
            return List.of();
        }
        return cluster.getNodeInfos().stream().
                      filter(n -> effectiveActivatedStateVersion(n, bundle) < version).
                      map(NodeInfo::getNode).
                      toList();
    }

    private static <E> String stringifyListWithLimits(List<E> list, int limit) {
        if (list.size() > limit) {
            var sub = list.subList(0, limit);
            return String.format("%s (... and %d more)",
                    sub.stream().map(E::toString).collect(Collectors.joining(", ")),
                    list.size() - limit);
        } else {
            return list.stream().map(E::toString).collect(Collectors.joining(", "));
        }
    }

    private String buildNodesNotYetConvergedMessage(long taskConvergeVersion) {
        var nodes = enumerateNodesNotYetAckedAtLeastVersion(taskConvergeVersion);
        if (nodes.isEmpty()) {
            return "";
        }
        return String.format("the following nodes have not converged to at least version %d: %s",
                taskConvergeVersion, stringifyListWithLimits(nodes, options.maxDivergentNodesPrintedInTaskErrorMessages()));
    }

    private boolean completeSatisfiedVersionDependentTasks() {
        int publishedVersion = systemStateBroadcaster.lastClusterStateVersionInSync();
        long queueSizeBefore = taskCompletionQueue.size();
        // Note: although version monotonicity of tasks in queue always should hold,
        // deadline monotonicity is not guaranteed to do so due to reconfigs of task
        // timeout durations. Means that tasks enqueued with shorter deadline duration
        // might be observed as having at least the same timeout as tasks enqueued during
        // a previous configuration. Current clock implementation is also susceptible to
        // skewing.
        final long now = timer.getCurrentTimeInMillis();
        while (!taskCompletionQueue.isEmpty()) {
            VersionDependentTaskCompletion taskCompletion = taskCompletionQueue.peek();
            // TODO expose and use monotonic clock instead of system clock
            if (publishedVersion >= taskCompletion.getMinimumVersion()) {
                context.log(logger, Level.FINE, () -> String.format("Deferred task of type '%s' has minimum version %d, published is %d; completing",
                                                    taskCompletion.getTask().getClass().getName(), taskCompletion.getMinimumVersion(), publishedVersion));
                taskCompletion.getTask().notifyCompleted();
                taskCompletionQueue.remove();
            } else if (taskCompletion.getDeadlineTimePointMs() <= now) {
                var details = buildNodesNotYetConvergedMessage(taskCompletion.getMinimumVersion());
                context.log(logger, Level.WARNING, () -> String.format("Deferred task of type '%s' has exceeded wait deadline; completing with failure (details: %s)",
                                                       taskCompletion.getTask().getClass().getName(), details));
                taskCompletion.getTask().handleFailure(RemoteClusterControllerTask.Failure.of(
                        RemoteClusterControllerTask.FailureCondition.DEADLINE_EXCEEDED, details));
                taskCompletion.getTask().notifyCompleted();
                taskCompletionQueue.remove();
            } else {
                break;
            }
        }
        return (taskCompletionQueue.size() != queueSizeBefore);
    }

    /**
     * A "consolidated" cluster state is guaranteed to have up-to-date information on which nodes are
     * up or down even when the whole cluster is down. The regular, published cluster state is not
     * normally updated to reflect node events when the cluster is down.
     */
    ClusterState consolidatedClusterState() {
        final ClusterState publishedState = stateVersionTracker.getVersionedClusterState();
        if (publishedState.getClusterState() == State.UP) {
            return publishedState; // Short-circuit; already represents latest node state
        }
        // Latest candidate state contains the most up to date state information, even if it may not
        // have been published yet.
        final ClusterState current = stateVersionTracker.getLatestCandidateState().getClusterState().clone();
        current.setVersion(publishedState.getVersion());
        return current;
    }

    /*
      System test observations:
      - a node that stops normally (U -> S) then goes down erroneously triggers premature crash handling
      - long time before content node state convergence (though this seems to be the case for legacy impl as well)
     */
    private boolean resyncLocallyCachedState() {
        boolean didWork = false;
        // Let non-master state gatherers update wanted states once in a while, so states generated and shown are close to valid.
        if ( ! isMaster && cycleCount % 100 == 0) {
            didWork = metricUpdater.forWork("loadWantedStates", () -> database.loadWantedStates(databaseContext));
            didWork |= metricUpdater.forWork("loadStartTimestamps", () -> database.loadStartTimestamps(cluster));
        }
        // If we have new slobrok information, update our cluster.
        didWork |= metricUpdater.forWork("updateCluster", () -> nodeLookup.updateCluster(cluster, this));

        // Send getNodeState requests to zero or more nodes.
        didWork |= metricUpdater.forWork("sendMessages", () -> stateGatherer.sendMessages(cluster, communicator, this));
        // Important: timer events must use a state with pending changes visible, or they might
        // trigger edge events multiple times.
        didWork |= metricUpdater.forWork(
                "watchTimers",
                () -> stateChangeHandler.watchTimers(cluster, stateVersionTracker.getLatestCandidateState().getClusterState(), this));

        didWork |= metricUpdater.forWork("recomputeClusterStateIfRequired", this::recomputeClusterStateIfRequired);

        if ( ! isStateGatherer) {
            if ( ! isMaster) {
                eventLog.add(new ClusterEvent(ClusterEvent.Type.MASTER_ELECTION, "This node just became node state gatherer as we are fleetcontroller master candidate.", timer.getCurrentTimeInMillis()));
                // Update versions to use so what is shown is closer to what is reality on the master
                stateVersionTracker.setVersionRetrievedFromZooKeeper(database.getLatestSystemStateVersion());
                stateChangeHandler.setStateChangedFlag();
            }
        }
        isStateGatherer = true;
        return didWork;
    }

    private void invokeCandidateStateListeners(ClusterStateBundle candidateBundle) {
        systemStateListeners.forEach(listener -> listener.handleNewCandidateState(candidateBundle));
    }

    private boolean hasPassedFirstStateBroadcastTimePoint(long timeNowMs) {
        return timeNowMs >= firstAllowedStateBroadcast || cluster.allStatesReported();
    }

    private boolean recomputeClusterStateIfRequired() {
        boolean stateWasChanged = false;
        if (mustRecomputeCandidateClusterState()) {
            stateChangeHandler.unsetStateChangedFlag();
            final AnnotatedClusterState candidate = computeCurrentAnnotatedState();
            // TODO test multiple bucket spaces configured
            // TODO what interaction do we want between generated and derived states wrt. auto group take-downs?
            final ClusterStateBundle candidateBundle = ClusterStateBundle.builder(candidate)
                    .bucketSpaces(configuredBucketSpaces)
                    .stateDeriver(createBucketSpaceStateDeriver())
                    .deferredActivation(options.enableTwoPhaseClusterStateActivation())
                    .feedBlock(createResourceExhaustionCalculator()
                            .inferContentClusterFeedBlockOrNull(cluster.getNodeInfos()))
                    .deriveAndBuild();
            stateVersionTracker.updateLatestCandidateStateBundle(candidateBundle);
            invokeCandidateStateListeners(candidateBundle);

            final long timeNowMs = timer.getCurrentTimeInMillis();
            if (hasPassedFirstStateBroadcastTimePoint(timeNowMs)
                && (stateVersionTracker.candidateChangedEnoughFromCurrentToWarrantPublish()
                    || stateVersionTracker.hasReceivedNewVersionFromZooKeeper()))
            {
                final ClusterStateBundle before = stateVersionTracker.getVersionedClusterStateBundle();

                stateVersionTracker.promoteCandidateToVersionedState(timeNowMs);
                emitEventsForAlteredStateEdges(before, stateVersionTracker.getVersionedClusterStateBundle(), timeNowMs);
                handleNewPublishedState(stateVersionTracker.getVersionedClusterStateBundle());
                stateWasChanged = true;
            }
        }
        /*
         * This works transparently for tasks that end up changing the current cluster state (i.e.
         * requiring a new state to be published) and for those whose changes are no-ops (because
         * the changes they request are already part of the current state). In the former case the
         * tasks will depend on the version that was generated based upon them. In the latter case
         * the tasks will depend on the version that is already published (or in the process of
         * being published).
         */
        scheduleVersionDependentTasksForFutureCompletion(stateVersionTracker.getCurrentVersion());
        return stateWasChanged;
    }

    private ClusterStateDeriver createBucketSpaceStateDeriver() {
        if (options.clusterHasGlobalDocumentTypes()) {
            return new MaintenanceWhenPendingGlobalMerges(stateVersionTracker.createMergePendingChecker(),
                    createDefaultSpaceMaintenanceTransitionConstraint());
        } else {
            return createIdentityClonedBucketSpaceStateDeriver();
        }
    }

    private ResourceExhaustionCalculator createResourceExhaustionCalculator() {
        return new ResourceExhaustionCalculator(options.clusterFeedBlockEnabled(),
                                                options.clusterFeedBlockLimit(),
                                                stateVersionTracker.getLatestCandidateStateBundle().getFeedBlockOrNull(),
                                                options.clusterFeedBlockNoiseLevel());
    }

    private static ClusterStateDeriver createIdentityClonedBucketSpaceStateDeriver() {
        return (state, space) -> state.clone();
    }

    private MaintenanceTransitionConstraint createDefaultSpaceMaintenanceTransitionConstraint() {
        AnnotatedClusterState currentDefaultSpaceState = stateVersionTracker.getVersionedClusterStateBundle()
                .getDerivedBucketSpaceStates().getOrDefault(FixedBucketSpaces.defaultSpace(), AnnotatedClusterState.emptyState());
        return UpEdgeMaintenanceTransitionConstraint.forPreviouslyPublishedState(currentDefaultSpaceState.getClusterState());
    }

    /**
     * Move tasks that are dependent on the most recently generated state being published into
     * a completion queue with a dependency on the provided version argument. Once that version
     * has been ACKed by all distributors in the system, those tasks will be marked as completed.
     */
    private void scheduleVersionDependentTasksForFutureCompletion(int completeAtVersion) {
        // TODO expose and use monotonic clock instead of system clock
        final long maxDeadlineTimePointMs = timer.getCurrentTimeInMillis() + options.getMaxDeferredTaskVersionWaitTime().toMillis();
        for (RemoteClusterControllerTask task : tasksPendingStateRecompute) {
            context.log(logger, Level.INFO, task + " will be completed at version " + completeAtVersion);
            taskCompletionQueue.add(new VersionDependentTaskCompletion(completeAtVersion, task, maxDeadlineTimePointMs));
        }
        tasksPendingStateRecompute.clear();
    }

    private AnnotatedClusterState computeCurrentAnnotatedState() {
        ClusterStateGenerator.Params params = ClusterStateGenerator.Params.fromOptions(options);
        params.currentTimeInMillis(timer.getCurrentTimeInMillis())
                .cluster(cluster)
                .lowestObservedDistributionBitCount(stateVersionTracker.getLowestObservedDistributionBits());
        return ClusterStateGenerator.generatedStateFrom(params);
    }

    private void emitEventsForAlteredStateEdges(final ClusterStateBundle fromState,
                                                final ClusterStateBundle toState,
                                                final long timeNowMs) {
        final List<Event> deltaEvents = EventDiffCalculator.computeEventDiff(
                EventDiffCalculator.params()
                        .cluster(cluster)
                        .fromState(fromState)
                        .toState(toState)
                        .currentTimeMs(timeNowMs)
                        .maxMaintenanceGracePeriodTimeMs(options.storageNodeMaxTransitionTimeMs()));
        for (Event event : deltaEvents) {
            eventLog.add(event, isMaster);
        }

        emitStateAppliedEvents(timeNowMs, fromState.getBaselineClusterState(), toState.getBaselineClusterState());
    }

    private void emitStateAppliedEvents(long timeNowMs, ClusterState fromClusterState, ClusterState toClusterState) {
        eventLog.add(new ClusterEvent(
                ClusterEvent.Type.SYSTEMSTATE,
                "New cluster state version " + toClusterState.getVersion() + ". Change from last: " +
                        fromClusterState.getTextualDifference(toClusterState),
                timeNowMs), isMaster);

        if (toClusterState.getDistributionBitCount() != fromClusterState.getDistributionBitCount()) {
            eventLog.add(new ClusterEvent(
                    ClusterEvent.Type.SYSTEMSTATE,
                    "Altering distribution bits in system from "
                            + fromClusterState.getDistributionBitCount() + " to " +
                            toClusterState.getDistributionBitCount(),
                    timeNowMs), isMaster);
        }
    }

    private boolean atFirstClusterStateSendTimeEdge() {
        // We only care about triggering a state recomputation for the master, which is the only
        // one allowed to actually broadcast any states.
        if (!isMaster || systemStateBroadcaster.hasBroadcastedClusterStateBundle()) {
            return false;
        }
        return hasPassedFirstStateBroadcastTimePoint(timer.getCurrentTimeInMillis());
    }

    private boolean mustRecomputeCandidateClusterState() {
        return stateChangeHandler.stateMayHaveChanged()
                || stateVersionTracker.bucketSpaceMergeCompletionStateHasChanged()
                || atFirstClusterStateSendTimeEdge();
    }

    private boolean handleLeadershipEdgeTransitions() {
        boolean didWork = false;
        if (masterElectionHandler.isMaster()) {
            if ( ! isMaster) {
                // If we just became master, restore state from ZooKeeper
                stateChangeHandler.setStateChangedFlag();
                systemStateBroadcaster.resetBroadcastedClusterStateBundle();

                stateVersionTracker.setVersionRetrievedFromZooKeeper(database.getLatestSystemStateVersion());
                ClusterStateBundle previousBundle = database.getLatestClusterStateBundle();
                database.loadStartTimestamps(cluster);
                database.loadWantedStates(databaseContext);
                // TODO determine if we need any specialized handling here if feed block is set in the loaded bundle

                context.log(logger, Level.INFO, () -> String.format("Loaded previous cluster state bundle from ZooKeeper: %s", previousBundle));
                stateVersionTracker.setClusterStateBundleRetrievedFromZooKeeper(previousBundle);

                eventLog.add(new ClusterEvent(ClusterEvent.Type.MASTER_ELECTION, "This node just became fleetcontroller master. Bumped version to "
                        + stateVersionTracker.getCurrentVersion() + " to be in line.", timer.getCurrentTimeInMillis()));
                long currentTime = timer.getCurrentTimeInMillis();
                firstAllowedStateBroadcast = currentTime + options.minTimeBeforeFirstSystemStateBroadcast();
                isMaster = true;
                inMasterMoratorium = true;
                context.log(logger, Level.FINE, () -> "At time " + currentTime + " we set first system state broadcast time to be "
                                      + options.minTimeBeforeFirstSystemStateBroadcast() + " ms after at time " + firstAllowedStateBroadcast + ".");
                didWork = true;
            }
            if (wantedStateChanged) {
                didWork |= database.saveWantedStates(databaseContext);
                wantedStateChanged = false;
            }
        } else {
            dropLeadershipState();
        }
        metricUpdater.updateMasterState(isMaster);
        return didWork;
    }

    private void dropLeadershipState() {
        if (isMaster) {
            eventLog.add(new ClusterEvent(ClusterEvent.Type.MASTER_ELECTION, "This node is no longer fleetcontroller master.", timer.getCurrentTimeInMillis()));
            firstAllowedStateBroadcast = Long.MAX_VALUE;
            failAllVersionDependentTasks();
        }
        wantedStateChanged = false;
        isMaster = false;
        inMasterMoratorium = false;
    }

    @Override
    public void run() {
        controllerThreadId = Thread.currentThread().getId();
        context.log(logger, Level.INFO, "Starting tick loop");
        try {
            processingCycle = true;
            while (isRunning()) {
                tick();
            }
            context.log(logger, Level.INFO, "Tick loop stopped");
        } catch (InterruptedException e) {
            context.log(logger, Level.INFO, "Event thread stopped by interrupt exception: ", e);
        } catch (Throwable t) {
            t.printStackTrace();
            context.log(logger, Level.SEVERE, "Fatal error killed fleet controller", t);
            synchronized (monitor) { running.set(false); }
            System.exit(1);
        } finally {
            prepareShutdownEdge();
        }
    }

    private void prepareShutdownEdge() {
        running.set(false);
        failAllVersionDependentTasks();
        synchronized (monitor) { monitor.notifyAll(); }
    }

    public final DatabaseHandler.DatabaseContext databaseContext = new DatabaseHandler.DatabaseContext() {
        @Override
        public ContentCluster getCluster() { return cluster; }
        @Override
        public FleetController getFleetController() { return FleetController.this; }
        @Override
        public NodeListener getNodeStateUpdateListener() { return FleetController.this; }
    };

    // For testing only
    public void waitForCompleteCycle(Duration timeout) {
        Instant endTime = Instant.now().plus(timeout);
        synchronized (monitor) {
            // To wait at least one complete cycle, if a cycle is already running we need to wait for the next one beyond.
            long wantedCycle = cycleCount + (processingCycle ? 2 : 1);
            waitingForCycle = true;
            try {
                while (cycleCount < wantedCycle) {
                    if (Instant.now().isAfter(endTime))
                        throw new IllegalStateException("Timed out waiting for cycle to complete. Not completed after " + timeout);
                    if ( !isRunning() )
                        throw new IllegalStateException("Fleetcontroller not running. Will never complete cycles");
                    try {
                        monitor.wait(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            } finally {
                waitingForCycle = false;
            }
        }
    }

    /**
     * This function might not be 100% threadsafe, as in theory cluster can be changing while accessed.
     * But it is only used in unit tests that should not trigger any thread issues. Don't want to add locks that reduce
     * live performance to remove a non-problem.
     */
    public void waitForNodesHavingSystemStateVersionEqualToOrAbove(int version, int nodeCount, Duration timeout) throws InterruptedException {
        Instant endTime = Instant.now().plus(timeout);
        synchronized (monitor) {
            while (true) {
                int ackedNodes = 0;
                for (NodeInfo node : cluster.getNodeInfos()) {
                    if (node.getClusterStateVersionBundleAcknowledged() >= version) {
                        ++ackedNodes;
                    }
                }
                if (ackedNodes >= nodeCount) {
                    context.log(logger, Level.INFO, ackedNodes + " nodes now have acked system state " + version + " or higher.");
                    return;
                }
                if (Instant.now().isAfter(endTime)) {
                    throw new IllegalStateException("Did not get " + nodeCount + " nodes to system state " + version + " within timeout of " + timeout);
                }
                monitor.wait(10);
            }
        }
    }

    public void waitForNodesInSlobrok(int distNodeCount, int storNodeCount, Duration timeout) throws InterruptedException {
        Instant endTime = Instant.now().plus(timeout);
        synchronized (monitor) {
            while (true) {
                int distCount = 0, storCount = 0;
                for (NodeInfo info : cluster.getNodeInfos()) {
                    if (info.isInSlobrok()) {
                        if (info.isDistributor()) ++distCount;
                        else ++storCount;
                    }
                }
                if (distCount == distNodeCount && storCount == storNodeCount) return;

                if (Instant.now().isAfter(endTime)) {
                    throw new IllegalStateException("Did not get all " + distNodeCount + " distributors and " + storNodeCount
                            + " storage nodes registered in slobrok within timeout of " + timeout + ". (Got "
                            + distCount + " distributors and " + storCount + " storage nodes)");
                }
                monitor.wait(10);
            }
        }
    }

    public boolean hasZookeeperConnection() { return !database.isClosed(); }

    // Used by unit tests.
    public int getSlobrokMirrorUpdates() { return ((SlobrokClient)nodeLookup).getMirror().updates(); }

    public ContentCluster getCluster() { return cluster; }

    public StatusHandler.ContainerStatusPageServer statusPageServer() { return statusPageServer; }

}
