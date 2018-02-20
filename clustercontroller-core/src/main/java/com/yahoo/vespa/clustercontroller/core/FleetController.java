// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.listeners.*;
import com.yahoo.vespa.clustercontroller.core.rpc.RPCCommunicator;
import com.yahoo.vespa.clustercontroller.core.rpc.RpcServer;
import com.yahoo.vespa.clustercontroller.core.rpc.SlobrokClient;
import com.yahoo.vespa.clustercontroller.core.status.*;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServerInterface;
import com.yahoo.vespa.clustercontroller.utils.util.MetricReporter;
import com.yahoo.vespa.clustercontroller.utils.util.NoMetricReporter;
import org.apache.commons.lang.exception.ExceptionUtils;

import java.io.FileNotFoundException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class FleetController implements NodeStateOrHostInfoChangeHandler, NodeAddedOrRemovedListener, SystemStateListener,
                                        Runnable, RemoteClusterControllerTaskScheduler {

    private static Logger log = Logger.getLogger(FleetController.class.getName());

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
    private final StatusPageServerInterface statusPageServer;
    private final RpcServer rpcServer;
    private final DatabaseHandler database;
    private final MasterElectionHandler masterElectionHandler;
    private Thread runner = null;
    private AtomicBoolean running = new AtomicBoolean(true);
    private FleetControllerOptions options;
    private FleetControllerOptions nextOptions;
    private final List<SystemStateListener> systemStateListeners = new LinkedList<>();
    private boolean processingCycle = false;
    private boolean wantedStateChanged = false;
    private long cycleCount = 0;
    private long nextStateSendTime = 0;
    private Long controllerThreadId = null;

    private boolean waitingForCycle = false;
    private StatusPageServer.PatternRequestRouter statusRequestRouter = new StatusPageServer.PatternRequestRouter();
    private final List<com.yahoo.vdslib.state.ClusterState> newStates = new ArrayList<>();
    private long configGeneration = -1;
    private long nextConfigGeneration = -1;
    private Queue<RemoteClusterControllerTask> remoteTasks = new LinkedList<>();
    private final MetricUpdater metricUpdater;

    private boolean isMaster = false;
    private boolean isStateGatherer = false;
    private long firstAllowedStateBroadcast = Long.MAX_VALUE;
    private long tickStartTime = Long.MAX_VALUE;

    private List<RemoteClusterControllerTask> tasksPendingStateRecompute = new ArrayList<>();
    // Invariant: queued task versions are monotonically increasing with queue position
    private Queue<VersionDependentTaskCompletion> taskCompletionQueue = new ArrayDeque<>();

    private final RunDataExtractor dataExtractor = new RunDataExtractor() {
        @Override
        public com.yahoo.vdslib.state.ClusterState getLatestClusterState() { return stateVersionTracker.getVersionedClusterState(); }
        @Override
        public FleetControllerOptions getOptions() { return options; }
        @Override
        public long getConfigGeneration() { return configGeneration; }
        @Override
        public ContentCluster getCluster() { return cluster; }
    };

    public FleetController(Timer timer,
                           EventLog eventLog,
                           ContentCluster cluster,
                           NodeStateGatherer nodeStateGatherer,
                           Communicator communicator,
                           StatusPageServerInterface statusPage,
                           RpcServer server,
                           NodeLookup nodeLookup,
                           DatabaseHandler database,
                           StateChangeHandler stateChangeHandler,
                           SystemStateBroadcaster systemStateBroadcaster,
                           MasterElectionHandler masterElectionHandler,
                           MetricUpdater metricUpdater,
                           FleetControllerOptions options) throws Exception
    {
        log.info("Starting up cluster controller " + options.fleetControllerIndex + " for cluster " + cluster.getName());
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
        this.stateVersionTracker = new StateVersionTracker();
        this.metricUpdater = metricUpdater;

        this.statusPageServer = statusPage;
        this.rpcServer = server;

        this.masterElectionHandler = masterElectionHandler;

        this.statusRequestRouter.addHandler(
                "^/node=([a-z]+)\\.(\\d+)$",
                new LegacyNodePageRequestHandler(timer, eventLog, cluster));
        this.statusRequestRouter.addHandler(
                "^/state.*",
                new NodeHealthRequestHandler(dataExtractor));
        this.statusRequestRouter.addHandler(
                "^/clusterstate",
                new ClusterStateRequestHandler(stateVersionTracker));
        this.statusRequestRouter.addHandler(
                "^/$",
                new LegacyIndexPageRequestHandler(
                    timer, options.showLocalSystemStatesInEventLog, cluster,
                    masterElectionHandler, stateVersionTracker,
                    eventLog, timer.getCurrentTimeInMillis(), dataExtractor));

        propagateOptions();
    }

    public static FleetController createForContainer(FleetControllerOptions options,
                                                     StatusPageServerInterface statusPageServer,
                                                     MetricReporter metricReporter) throws Exception {
        Timer timer = new RealTimer();
        return create(options, timer, statusPageServer, null, metricReporter);
    }

    public static FleetController createForStandAlone(FleetControllerOptions options) throws Exception {
        Timer timer = new RealTimer();
        RpcServer rpcServer = new RpcServer(timer, timer, options.clusterName, options.fleetControllerIndex, options.slobrokBackOffPolicy);
        StatusPageServer statusPageServer = new StatusPageServer(timer, timer, options.httpPort);
        return create(options, timer, statusPageServer, rpcServer, new NoMetricReporter());
    }

    private static FleetController create(FleetControllerOptions options,
                                          Timer timer,
                                          StatusPageServerInterface statusPageServer,
                                          RpcServer rpcServer,
                                          MetricReporter metricReporter) throws Exception
    {
        MetricUpdater metricUpdater = new MetricUpdater(metricReporter, options.fleetControllerIndex);
        EventLog log = new EventLog(timer, metricUpdater);
        ContentCluster cluster = new ContentCluster(
                options.clusterName,
                options.nodes,
                options.storageDistribution,
                options.minStorageNodesUp,
                options.minRatioOfStorageNodesUp);
        NodeStateGatherer stateGatherer = new NodeStateGatherer(timer, timer, log);
        Communicator communicator = new RPCCommunicator(
                timer,
                options.fleetControllerIndex,
                options.nodeStateRequestTimeoutMS,
                options.nodeStateRequestTimeoutEarliestPercentage,
                options.nodeStateRequestTimeoutLatestPercentage,
                options.nodeStateRequestRoundTripTimeMaxSeconds);
        DatabaseHandler database = new DatabaseHandler(timer, options.zooKeeperServerAddress, options.fleetControllerIndex, timer);
        NodeLookup lookUp = new SlobrokClient(timer);
        StateChangeHandler stateGenerator = new StateChangeHandler(timer, log, metricUpdater);
        SystemStateBroadcaster stateBroadcaster = new SystemStateBroadcaster(timer, timer);
        MasterElectionHandler masterElectionHandler = new MasterElectionHandler(options.fleetControllerIndex, options.fleetControllerCount, timer, timer);
        FleetController controller = new FleetController(
                timer, log, cluster, stateGatherer, communicator, statusPageServer, rpcServer, lookUp, database, stateGenerator, stateBroadcaster, masterElectionHandler, metricUpdater, options);
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
            return masterElectionHandler.isMaster();
        }
    }

    public ClusterState getClusterState() {
        synchronized (monitor) {
            return systemStateBroadcaster.getClusterState();
        }
    }

    public void schedule(RemoteClusterControllerTask task) {
        synchronized (monitor) {
            log.fine("Scheduled remote task " + task.getClass().getName() + " for execution");
            remoteTasks.add(task);
        }
    }

    /** Used for unit testing. */
    public void addSystemStateListener(SystemStateListener listener) {
        synchronized (systemStateListeners) {
            systemStateListeners.add(listener);
            // Always give cluster state listeners the current state, in case acceptable state has come before listener is registered.
            com.yahoo.vdslib.state.ClusterState state = getSystemState();
            if (state == null) throw new NullPointerException("Cluster state should never be null at this point");
            listener.handleNewSystemState(state);
        }
    }

    public FleetControllerOptions getOptions() {
        synchronized(monitor) {
            return options.clone();
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

    public int getHttpPort() { return statusPageServer.getPort(); }
    public int getRpcPort() { return rpcServer.getPort(); }

    public void shutdown() throws InterruptedException, java.io.IOException {
        if (runner != null && isRunning()) {
            log.log(LogLevel.INFO,  "Joining event thread.");
            running.set(false);
            synchronized(monitor) { monitor.notifyAll(); }
            runner.join();
        }
        log.log(LogLevel.INFO,  "Fleetcontroller done shutting down event thread.");
        controllerThreadId = Thread.currentThread().getId();
        database.shutdown(this);

        if (statusPageServer != null) {
            statusPageServer.shutdown();
        }
        if (rpcServer != null) {
            rpcServer.shutdown();
        }
        communicator.shutdown();
        nodeLookup.shutdown();
    }

    public void updateOptions(FleetControllerOptions options, long configGeneration) {
        synchronized(monitor) {
            assert(this.options.fleetControllerIndex == options.fleetControllerIndex);
            log.log(LogLevel.INFO, "Fleetcontroller " + options.fleetControllerIndex + " has new options");
            nextOptions = options.clone();
            nextConfigGeneration = configGeneration;
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
    public void handleUpdatedHostInfo(NodeInfo nodeInfo, HostInfo newHostInfo) {
        verifyInControllerThread();
        stateVersionTracker.handleUpdatedHostInfo(stateChangeHandler.getHostnames(), nodeInfo, newHostInfo);
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

    public void handleNewSystemState(com.yahoo.vdslib.state.ClusterState state) {
        verifyInControllerThread();
        newStates.add(state);
        metricUpdater.updateClusterStateMetrics(cluster, state);
        systemStateBroadcaster.handleNewSystemState(state);
        // Iff master, always store new version in ZooKeeper _before_ publishing to any
        // nodes so that a cluster controller crash after publishing but before a successful
        // ZK store will not risk reusing the same version number.
        if (masterElectionHandler.isMaster()) {
            storeClusterStateVersionToZooKeeper(state);
        }
    }

    private void storeClusterStateVersionToZooKeeper(ClusterState state) {
        try {
            database.saveLatestSystemStateVersion(databaseContext, state.getVersion());
        } catch (InterruptedException e) {
            // Rethrow as RuntimeException to propagate exception up to main thread method.
            // Don't want to hide failures to write cluster state version.
            throw new RuntimeException("ZooKeeper write interrupted", e);
        }
    }

    /**
     * This function gives data of the current state in master election.
     * The keys in the given map are indexes of fleet controllers.
     * The values are what fleetcontroller that fleetcontroller wants to
     * become master.
     *
     * If more than half the fleetcontrollers want a node to be master and
     * that node also wants itself as master, that node is the single master.
     * If this condition is not met, there is currently no master.
     */
    public void handleFleetData(Map<Integer, Integer> data) {
        verifyInControllerThread();
        log.log(LogLevel.SPAM, "Sending fleet data event on to master election handler");
        metricUpdater.updateMasterElectionMetrics(data);
        masterElectionHandler.handleFleetData(data);
    }

    /**
     * Called when we can no longer contact database.
     */
    public void lostDatabaseConnection() {
        verifyInControllerThread();
        masterElectionHandler.lostDatabaseConnection();
    }

    private void failAllVersionDependentTasks() {
        tasksPendingStateRecompute.forEach(task -> {
            task.handleFailure(RemoteClusterControllerTask.FailureCondition.LEADERSHIP_LOST);
            task.notifyCompleted();
        });
        tasksPendingStateRecompute.clear();
        taskCompletionQueue.forEach(task -> {
            task.getTask().handleFailure(RemoteClusterControllerTask.FailureCondition.LEADERSHIP_LOST);
            task.getTask().notifyCompleted();
        });
        taskCompletionQueue.clear();
    }

    /** Called when all distributors have acked newest cluster state version. */
    public void handleAllDistributorsInSync(DatabaseHandler database, DatabaseHandler.Context context) throws InterruptedException {
        Set<ConfiguredNode> nodes = new HashSet<>(cluster.clusterInfo().getConfiguredNodes().values());
        ClusterState currentState = stateVersionTracker.getVersionedClusterState();
        log.fine(() -> String.format("All distributors have ACKed cluster state version %d", currentState.getVersion()));
        stateChangeHandler.handleAllDistributorsInSync(currentState, nodes, database, context);
    }

    private boolean changesConfiguredNodeSet(Collection<ConfiguredNode> newNodes) {
        if (newNodes.size() != cluster.getConfiguredNodes().size()) return true;
        if (! cluster.getConfiguredNodes().values().containsAll(newNodes)) return true;

        // Check retirement changes
        for (ConfiguredNode node : newNodes) {
            if (node.retired() != cluster.getConfiguredNodes().get(node.index()).retired())
                return true;
        }

        return false;
    }

    /** This is called when the options field has been set to a new set of options */
    private void propagateOptions() throws java.io.IOException, ListenFailedException {
        verifyInControllerThread();

        if (changesConfiguredNodeSet(options.nodes)) {
            // Force slobrok node re-fetch in case of changes to the set of configured nodes
            cluster.setSlobrokGenerationCount(0);
        }

        communicator.propagateOptions(options);

        if (nodeLookup instanceof SlobrokClient)
            ((SlobrokClient)nodeLookup).setSlobrokConnectionSpecs(options.slobrokConnectionSpecs);
        eventLog.setMaxSize(options.eventLogMaxSize, options.eventNodeLogMaxSize);
        cluster.setPollingFrequency(options.statePollingFrequency);
        cluster.setDistribution(options.storageDistribution);
        cluster.setNodes(options.nodes);
        cluster.setMinRatioOfStorageNodesUp(options.minRatioOfStorageNodesUp);
        cluster.setMinStorageNodesUp(options.minStorageNodesUp);
        database.setZooKeeperAddress(options.zooKeeperServerAddress);
        database.setZooKeeperSessionTimeout(options.zooKeeperSessionTimeout);
        stateGatherer.setMaxSlobrokDisconnectGracePeriod(options.maxSlobrokDisconnectGracePeriod);
        stateGatherer.setNodeStateRequestTimeout(options.nodeStateRequestTimeoutMS);

        // TODO: remove as many temporal parameter dependencies as possible here. Currently duplication of state.
        stateChangeHandler.reconfigureFromOptions(options);
        stateChangeHandler.setStateChangedFlag(); // Always trigger state recomputation after reconfig

        masterElectionHandler.setFleetControllerCount(options.fleetControllerCount);
        masterElectionHandler.setMasterZooKeeperCooldownPeriod(options.masterZooKeeperCooldownPeriod);

        if (rpcServer != null) {
            rpcServer.setMasterElectionHandler(masterElectionHandler);
            try{
                rpcServer.setSlobrokConnectionSpecs(options.slobrokConnectionSpecs, options.rpcPort);
            } catch (ListenFailedException e) {
                log.log(LogLevel.WARNING, "Failed to bind RPC server to port " + options.rpcPort +". This may be natural if cluster has altered the services running on this node: " + e.getMessage());
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed to initialize RPC server socket: " + e.getMessage());
            }
        }

        if (statusPageServer != null) {
            try{
                statusPageServer.setPort(options.httpPort);
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed to initialize status server socket. This may be natural if cluster has altered the services running on this node: " + e.getMessage());
            }
        }

        long currentTime = timer.getCurrentTimeInMillis();
        nextStateSendTime = Math.min(currentTime + options.minTimeBetweenNewSystemStates, nextStateSendTime);
        configGeneration = nextConfigGeneration;
        nextConfigGeneration = -1;
    }

    public StatusPageResponse fetchStatusPage(StatusPageServer.HttpRequest httpRequest) {
        verifyInControllerThread();
        StatusPageResponse.ResponseCode responseCode;
        String message;
        String hiddenMessage = "";
        try {
            StatusPageServer.RequestHandler handler = statusRequestRouter.resolveHandler(httpRequest);
            if (handler == null) {
                throw new FileNotFoundException("No handler found for request: " + httpRequest.getPath());
            }
            return handler.handle(httpRequest);
        } catch (FileNotFoundException e) {
            responseCode = StatusPageResponse.ResponseCode.NOT_FOUND;
            message = e.getMessage();
        } catch (Exception e) {
            responseCode = StatusPageResponse.ResponseCode.INTERNAL_SERVER_ERROR;
            message = "Internal Server Error";
            hiddenMessage = ExceptionUtils.getStackTrace(e);
            log.log(LogLevel.DEBUG, "Unknown exception thrown for request " + httpRequest.getRequest() +
                    ": " + hiddenMessage);
        }

        TimeZone tz = TimeZone.getTimeZone("UTC");
        long currentTime = timer.getCurrentTimeInMillis();
        StatusPageResponse response = new StatusPageResponse();
        StringBuilder content = new StringBuilder();
        response.setContentType("text/html");
        response.setResponseCode(responseCode);
        content.append("<!-- Answer to request " + httpRequest.getRequest() + " -->\n");
        content.append("<p>UTC time when creating this page: ").append(RealTimer.printDateNoMilliSeconds(currentTime, tz)).append("</p>");
        response.writeHtmlHeader(content, message);
        response.writeHtmlFooter(content, hiddenMessage);
        response.writeContent(content.toString());

        return response;
    }

    public void tick() throws Exception {
        synchronized (monitor) {
            boolean didWork;
            didWork = database.doNextZooKeeperTask(databaseContext);
            didWork |= updateMasterElectionState();
            didWork |= handleLeadershipEdgeTransitions();
            stateChangeHandler.setMaster(isMaster);

            if ( ! isRunning()) { return; }
            // Process zero or more getNodeState responses that we have received.
            didWork |= stateGatherer.processResponses(this);

            if ( ! isRunning()) { return; }

            if (masterElectionHandler.isAmongNthFirst(options.stateGatherCount)) {
                didWork |= resyncLocallyCachedState();
            } else {
                stepDownAsStateGatherer();
            }

            if ( ! isRunning()) { return; }
            didWork |= systemStateBroadcaster.processResponses();
            if ( ! isRunning()) { return; }
            if (masterElectionHandler.isMaster()) {
                didWork |= broadcastClusterStateToEligibleNodes();
                systemStateBroadcaster.checkIfClusterStateIsAckedByAllDistributors(database, databaseContext, this);
            }

            if ( ! isRunning()) { return; }
            didWork |= processAnyPendingStatusPageRequest();
            if ( ! isRunning()) { return; }
            if (rpcServer != null) {
                didWork |= rpcServer.handleRpcRequests(cluster, consolidatedClusterState(), this, this);
            }

            if ( ! isRunning()) { return; }
            didWork |= processNextQueuedRemoteTask();
            didWork |= completeSatisfiedVersionDependentTasks();

            processingCycle = false;
            ++cycleCount;
            long tickStopTime = timer.getCurrentTimeInMillis();
            if (tickStopTime >= tickStartTime) {
                metricUpdater.addTickTime(tickStopTime - tickStartTime, didWork);
            }
            if ( ! didWork && ! waitingForCycle) {
                monitor.wait(options.cycleWaitTime);
            }
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

    private boolean updateMasterElectionState() throws InterruptedException {
        try {
            return masterElectionHandler.watchMasterElection(database, databaseContext);
        } catch (InterruptedException e) {
            throw (InterruptedException) new InterruptedException("Interrupted").initCause(e);
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Failed to watch master election: " + e.toString());
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
            log.log(LogLevel.ERROR, "Failed to handle new fleet controller config", e);
        }
    }

    private boolean processAnyPendingStatusPageRequest() {
        if (statusPageServer != null) {
            StatusPageServer.HttpRequest statusRequest = statusPageServer.getCurrentHttpRequest();
            if (statusRequest != null) {
                statusPageServer.answerCurrentStatusRequest(fetchStatusPage(statusRequest));
                return true;
            }
        }
        return false;
    }

    private boolean broadcastClusterStateToEligibleNodes() throws InterruptedException {
        boolean sentAny = false;
        // Give nodes a fair chance to respond first time to state gathering requests, so we don't
        // disturb system when we take over. Allow anyways if we have states from all nodes.
        long currentTime = timer.getCurrentTimeInMillis();
        if ((currentTime >= firstAllowedStateBroadcast || cluster.allStatesReported())
            && currentTime >= nextStateSendTime)
        {
            if (currentTime < firstAllowedStateBroadcast) {
                log.log(LogLevel.DEBUG, "Not set to broadcast states just yet, but as we have gotten info from all nodes we can do so safely.");
                // Reset timer to only see warning once.
                firstAllowedStateBroadcast = currentTime;
            }
            sentAny = systemStateBroadcaster.broadcastNewState(database, databaseContext, communicator, this);
            if (sentAny) {
                nextStateSendTime = currentTime + options.minTimeBetweenNewSystemStates;
            }
        }
        return sentAny;
    }

    private void propagateNewStatesToListeners() {
        if ( ! newStates.isEmpty()) {
            synchronized (systemStateListeners) {
                for (ClusterState state : newStates) {
                    for(SystemStateListener listener : systemStateListeners) {
                        listener.handleNewSystemState(state);
                    }
                }
                newStates.clear();
            }
        }
    }

    private boolean processNextQueuedRemoteTask() {
        if ( ! remoteTasks.isEmpty()) {
            final RemoteClusterControllerTask.Context context = createRemoteTaskProcessingContext();
            final RemoteClusterControllerTask task = remoteTasks.poll();
            log.finest(() -> String.format("Processing remote task of type '%s'", task.getClass().getName()));
            task.doRemoteFleetControllerTask(context);
            if (taskMayBeCompletedImmediately(task)) {
                log.finest(() -> String.format("Done processing remote task of type '%s'", task.getClass().getName()));
                task.notifyCompleted();
            } else {
                log.finest(() -> String.format("Remote task of type '%s' queued until state recomputation", task.getClass().getName()));
                tasksPendingStateRecompute.add(task);
            }
            return true;
        }
        return false;
    }

    private boolean taskMayBeCompletedImmediately(RemoteClusterControllerTask task) {
        // We cannot introduce a version barrier for tasks when we're not the master (and therefore will not publish new versions).
        return (!task.hasVersionAckDependency() || task.isFailed() || !masterElectionHandler.isMaster());
    }

    private RemoteClusterControllerTask.Context createRemoteTaskProcessingContext() {
        final RemoteClusterControllerTask.Context context = new RemoteClusterControllerTask.Context();
        context.cluster = cluster;
        context.currentState = consolidatedClusterState();
        context.masterInfo = masterElectionHandler;
        context.nodeStateOrHostInfoChangeHandler = this;
        context.nodeAddedOrRemovedListener = this;
        return context;
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
                log.fine(() -> String.format("Deferred task of type '%s' has minimum version %d, published is %d; completing",
                        taskCompletion.getTask().getClass().getName(), taskCompletion.getMinimumVersion(), publishedVersion));
                taskCompletion.getTask().notifyCompleted();
                taskCompletionQueue.remove();
            } else if (taskCompletion.getDeadlineTimePointMs() <= now) {
                log.fine(() -> String.format("Deferred task of type '%s' has exceeded wait deadline; completing with failure",
                        taskCompletion.getTask().getClass().getName()));
                taskCompletion.getTask().handleFailure(RemoteClusterControllerTask.FailureCondition.DEADLINE_EXCEEDED);
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
    private boolean resyncLocallyCachedState() throws InterruptedException {
        boolean didWork = false;
        // Let non-master state gatherers update wanted states once in a while, so states generated and shown are close to valid.
        if ( ! isMaster && cycleCount % 100 == 0) {
            didWork = database.loadWantedStates(databaseContext);
            didWork |= database.loadStartTimestamps(cluster);
        }
        // If we have new slobrok information, update our cluster.
        didWork |= nodeLookup.updateCluster(cluster, this);

        // Send getNodeState requests to zero or more nodes.
        didWork |= stateGatherer.sendMessages(cluster, communicator, this);
        // Important: timer events must use a consolidated state, or they might trigger edge events multiple times.
        didWork |= stateChangeHandler.watchTimers(cluster, consolidatedClusterState(), this);

        didWork |= recomputeClusterStateIfRequired();

        if ( ! isStateGatherer) {
            if ( ! isMaster) {
                eventLog.add(new ClusterEvent(ClusterEvent.Type.MASTER_ELECTION, "This node just became node state gatherer as we are fleetcontroller master candidate.", timer.getCurrentTimeInMillis()));
                // Update versions to use so what is shown is closer to what is reality on the master
                stateVersionTracker.setVersionRetrievedFromZooKeeper(database.getLatestSystemStateVersion());
            }
        }
        isStateGatherer = true;
        return didWork;
    }

    private boolean recomputeClusterStateIfRequired() {
        boolean stateWasChanged = false;
        if (mustRecomputeCandidateClusterState()) {
            stateChangeHandler.unsetStateChangedFlag();
            final AnnotatedClusterState candidate = computeCurrentAnnotatedState();
            stateVersionTracker.updateLatestCandidateState(candidate);

            if (stateVersionTracker.candidateChangedEnoughFromCurrentToWarrantPublish()
                    || stateVersionTracker.hasReceivedNewVersionFromZooKeeper())
            {
                final long timeNowMs = timer.getCurrentTimeInMillis();
                final AnnotatedClusterState before = stateVersionTracker.getAnnotatedVersionedClusterState();

                stateVersionTracker.promoteCandidateToVersionedState(timeNowMs);
                emitEventsForAlteredStateEdges(before, stateVersionTracker.getAnnotatedVersionedClusterState(), timeNowMs);
                handleNewSystemState(stateVersionTracker.getVersionedClusterState());
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

    /**
     * Move tasks that are dependent on the most recently generated state being published into
     * a completion queue with a dependency on the provided version argument. Once that version
     * has been ACKed by all distributors in the system, those tasks will be marked as completed.
     */
    private void scheduleVersionDependentTasksForFutureCompletion(int completeAtVersion) {
        // TODO expose and use monotonic clock instead of system clock
        final long deadlineTimePointMs = timer.getCurrentTimeInMillis() + options.getMaxDeferredTaskVersionWaitTime().toMillis();
        for (RemoteClusterControllerTask task : tasksPendingStateRecompute) {
            log.finest(() -> String.format("Adding task of type '%s' to be completed at version %d",
                    task.getClass().getName(), completeAtVersion));
            taskCompletionQueue.add(new VersionDependentTaskCompletion(completeAtVersion, task, deadlineTimePointMs));
        }
        tasksPendingStateRecompute.clear();
    }

    private AnnotatedClusterState computeCurrentAnnotatedState() {
        ClusterStateGenerator.Params params = ClusterStateGenerator.Params.fromOptions(options);
        params.currentTimeInMilllis(timer.getCurrentTimeInMillis())
                .cluster(cluster)
                .lowestObservedDistributionBitCount(stateVersionTracker.getLowestObservedDistributionBits());
        return ClusterStateGenerator.generatedStateFrom(params);
    }

    private void emitEventsForAlteredStateEdges(final AnnotatedClusterState fromState,
                                                final AnnotatedClusterState toState,
                                                final long timeNowMs) {
        final List<Event> deltaEvents = EventDiffCalculator.computeEventDiff(
                EventDiffCalculator.params()
                        .cluster(cluster)
                        .fromState(fromState)
                        .toState(toState)
                        .currentTimeMs(timeNowMs));
        for (Event event : deltaEvents) {
            eventLog.add(event, isMaster);
        }

        emitStateAppliedEvents(timeNowMs, fromState.getClusterState(), toState.getClusterState());
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

    private boolean mustRecomputeCandidateClusterState() {
        return stateChangeHandler.stateMayHaveChanged() || stateVersionTracker.hasReceivedNewVersionFromZooKeeper();
    }

    private boolean handleLeadershipEdgeTransitions() throws InterruptedException {
        boolean didWork = false;
        if (masterElectionHandler.isMaster()) {
            if ( ! isMaster) {
                metricUpdater.becameMaster();
                // If we just became master, restore wanted states from database
                stateVersionTracker.setVersionRetrievedFromZooKeeper(database.getLatestSystemStateVersion());
                didWork = database.loadStartTimestamps(cluster);
                didWork |= database.loadWantedStates(databaseContext);
                eventLog.add(new ClusterEvent(ClusterEvent.Type.MASTER_ELECTION, "This node just became fleetcontroller master. Bumped version to "
                        + stateVersionTracker.getCurrentVersion() + " to be in line.", timer.getCurrentTimeInMillis()));
                long currentTime = timer.getCurrentTimeInMillis();
                firstAllowedStateBroadcast = currentTime + options.minTimeBeforeFirstSystemStateBroadcast;
                log.log(LogLevel.DEBUG, "At time " + currentTime + " we set first system state broadcast time to be "
                        + options.minTimeBeforeFirstSystemStateBroadcast + " ms after at time " + firstAllowedStateBroadcast + ".");
            }
            isMaster = true;
            if (wantedStateChanged) {
                database.saveWantedStates(databaseContext);
                wantedStateChanged = false;
            }
        } else {
            if (isMaster) {
                eventLog.add(new ClusterEvent(ClusterEvent.Type.MASTER_ELECTION, "This node is no longer fleetcontroller master.", timer.getCurrentTimeInMillis()));
                firstAllowedStateBroadcast = Long.MAX_VALUE;
                metricUpdater.noLongerMaster();
                failAllVersionDependentTasks();
            }
            wantedStateChanged = false;
            isMaster = false;
        }
        return didWork;
    }

    public void run() {
        controllerThreadId = Thread.currentThread().getId();
        try {
            processingCycle = true;
            while( isRunning() ) {
                tick();
            }
        } catch (InterruptedException e) {
            log.log(LogLevel.DEBUG, "Event thread stopped by interrupt exception: " + e);
        } catch (Throwable t) {
            t.printStackTrace();
            log.log(LogLevel.ERROR, "Fatal error killed fleet controller", t);
            synchronized (monitor) { running.set(false); }
            System.exit(1);
        } finally {
            running.set(false);
            failAllVersionDependentTasks();
            synchronized (monitor) { monitor.notifyAll(); }
        }
    }

    public DatabaseHandler.Context databaseContext = new DatabaseHandler.Context() {
        @Override
        public ContentCluster getCluster() { return cluster; }
        @Override
        public FleetController getFleetController() { return FleetController.this; }
        @Override
        public NodeAddedOrRemovedListener getNodeAddedOrRemovedListener() { return FleetController.this; }
        @Override
        public NodeStateOrHostInfoChangeHandler getNodeStateUpdateListener() { return FleetController.this; }
    };

    public void waitForCompleteCycle(long timeoutMS) {
        long endTime = System.currentTimeMillis() + timeoutMS;
        synchronized (monitor) {
            // To wait at least one complete cycle, if a cycle is already running we need to wait for the next one beyond.
            long wantedCycle = cycleCount + (processingCycle ? 2 : 1);
            waitingForCycle = true;
            try{
                while (cycleCount < wantedCycle) {
                    if (System.currentTimeMillis() > endTime) throw new IllegalStateException("Timed out waiting for cycle to complete. Not completed after " + timeoutMS + " ms.");
                    if ( !isRunning() ) throw new IllegalStateException("Fleetcontroller not running. Will never complete cycles");
                    try{ monitor.wait(100); } catch (InterruptedException e) {}
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
    public void waitForNodesHavingSystemStateVersionEqualToOrAbove(int version, int nodeCount, int timeout) throws InterruptedException {
        long maxTime = System.currentTimeMillis() + timeout;
        synchronized (monitor) {
            while (true) {
                int ackedNodes = 0;
                for (NodeInfo node : cluster.getNodeInfo()) {
                    if (node.getSystemStateVersionAcknowledged() >= version) {
                        ++ackedNodes;
                    }
                }
                if (ackedNodes >= nodeCount) {
                    log.log(LogLevel.INFO, ackedNodes + " nodes now have acked system state " + version + " or higher.");
                    return;
                }
                long remainingTime = maxTime - System.currentTimeMillis();
                if (remainingTime <= 0) {
                    throw new IllegalStateException("Did not get " + nodeCount + " nodes to system state " + version + " within timeout of " + timeout + " milliseconds.");
                }
                monitor.wait(10);
            }
        }
    }

    public void waitForNodesInSlobrok(int distNodeCount, int storNodeCount, int timeoutMillis) throws InterruptedException {
        long maxTime = System.currentTimeMillis() + timeoutMillis;
        synchronized (monitor) {
            while (true) {
                int distCount = 0, storCount = 0;
                for (NodeInfo info : cluster.getNodeInfo()) {
                    if (!info.isRpcAddressOutdated()) {
                        if (info.isDistributor()) ++distCount;
                        else ++storCount;
                    }
                }
                if (distCount == distNodeCount && storCount == storNodeCount) return;

                long remainingTime = maxTime - System.currentTimeMillis();
                if (remainingTime <= 0) {
                    throw new IllegalStateException("Did not get all " + distNodeCount + " distributors and " + storNodeCount
                            + " storage nodes registered in slobrok within timeout of " + timeoutMillis + " ms. (Got "
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

    public List<NodeEvent> getNodeEvents(Node n) { return eventLog.getNodeEvents(n); }

    public EventLog getEventLog() {
        return eventLog;
    }

}
