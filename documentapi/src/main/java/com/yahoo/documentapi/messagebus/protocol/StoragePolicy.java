// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.routing.RoutingNodeIterator;
import com.yahoo.messagebus.routing.VerbatimDirective;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Routing policy to determine which distributor in a storage cluster to send data to.
 * Using different key=value parameters separated by semicolon (";"), the user can control which cluster to send to.
 *
 * cluster=[clusterName] (Mandatory, determines the cluster name)
 * config=[config] (Optional, a comma separated list of config servers to use. Used to talk to clusters not defined in this vespa application)
 * slobrokconfigid=[id] (Optional, use given config id for slobrok instead of default)
 * clusterconfigid=[id] (Optional, use given config id for distribution instead of default)
 *
 * @author Haakon Humberset
 */
public class StoragePolicy extends ExternalSlobrokPolicy {

    private static final Logger log = Logger.getLogger(StoragePolicy.class.getName());
    public static final String owningBucketStates = "uim";
    private static final String upStates = "ui";

    /** This class merely generates slobrok a host pattern for a given distributor. */
    public static class SlobrokHostPatternGenerator {
        private final String base;
        private final String all;
        SlobrokHostPatternGenerator(String clusterName) {
            base = "storage/cluster." + clusterName + "/distributor/";
            all = base + "*/default";

        }

        /**
         * Find host pattern of the hosts that are valid targets for this request.
         * @param distributor Set to -1 if any distributor is valid target.
         */
        public String getDistributorHostPattern(Integer distributor) {
            return (distributor == null) ? all : (base + distributor + "/default");
        }
    }

    /** Helper class to match a host pattern with node to use. */
    public abstract static class HostFetcher {
        private AtomicInteger safeRequiredUpPercentageToSendToKnownGoodNodes = new AtomicInteger(60);
        private AtomicReference<List<Integer>> safeValidRandomTargets = new AtomicReference<>(new CopyOnWriteArrayList<>());
        private AtomicInteger safeTotalTargets = new AtomicInteger(1);
        protected final Random randomizer = new Random(12345); // Use same randomizer each time to make unit testing easy.

        void setRequiredUpPercentageToSendToKnownGoodNodes(int percent) { this.safeRequiredUpPercentageToSendToKnownGoodNodes.set(percent); }

        void updateValidTargets(ClusterState state) {
            List<Integer> validRandomTargets = new ArrayList<>();
            for (int i=0; i<state.getNodeCount(NodeType.DISTRIBUTOR); ++i) {
                if (state.getNodeState(new Node(NodeType.DISTRIBUTOR, i)).getState().oneOf(upStates)) validRandomTargets.add(i);
            }
            this.safeValidRandomTargets.set(new CopyOnWriteArrayList<>(validRandomTargets));
            safeTotalTargets.set(state.getNodeCount(NodeType.DISTRIBUTOR));
        }
        public abstract String getTargetSpec(Integer distributor, RoutingContext context);
        String getRandomTargetSpec(RoutingContext context) {
            List<Integer> validRandomTargets = safeValidRandomTargets.get();
            // Try to use list of random targets, if at least X % of the nodes are up
            int totalTargets = safeTotalTargets.get();
            int requiredUpPercentageToSendToKnownGoodNodes = safeRequiredUpPercentageToSendToKnownGoodNodes.get();
            while (100 * validRandomTargets.size() / totalTargets >= requiredUpPercentageToSendToKnownGoodNodes) {
                int randIndex = randomizer.nextInt(validRandomTargets.size());
                String targetSpec = getTargetSpec(validRandomTargets.get(randIndex), context);
                if (targetSpec != null) {
                    context.trace(3, "Sending to random node seen up in cluster state");
                    return targetSpec;
                }
                validRandomTargets.remove(randIndex);
            }
            context.trace(3, "Too few nodes seen up in state. Sending totally random.");
            return getTargetSpec(null, context);
        }
        public void close() {}
    }

    /** Host fetcher using a slobrok mirror to find the hosts. */
    public static class SlobrokHostFetcher extends HostFetcher {
        private final SlobrokHostPatternGenerator patternGenerator;
        final ExternalSlobrokPolicy policy;

        SlobrokHostFetcher(SlobrokHostPatternGenerator patternGenerator, ExternalSlobrokPolicy policy) {
            this.patternGenerator = patternGenerator;
            this.policy = policy;
        }

        private List<Mirror.Entry> getEntries(String hostPattern, RoutingContext context) {
            return policy.lookup(context, hostPattern);
        }

        private String convertSlobrokNameToSessionName(String slobrokName) { return slobrokName + "/default"; }

        public IMirror getMirror(RoutingContext context) { return context.getMirror(); }

        @Override
        public String getTargetSpec(Integer distributor, RoutingContext context) {
            List<Mirror.Entry> arr = getEntries(patternGenerator.getDistributorHostPattern(distributor), context);
            if (arr.isEmpty()) return null;
            if (distributor != null) {
                if (arr.size() == 1) {
                    return convertSlobrokNameToSessionName(arr.get(0).getSpec());
                } else {
                    log.log(LogLevel.WARNING, "Got " + arr.size() + " matches for a distributor.");
                }
            } else {
                return convertSlobrokNameToSessionName(arr.get(randomizer.nextInt(arr.size())).getSpec());
            }
            return null;
        }
    }

    static class TargetCachingSlobrokHostFetcher extends SlobrokHostFetcher {

        /**
         * Distributor index to resolved RPC spec cache for a single given Slobrok
         * update generation. Uses a thread safe COW map which will grow until stable.
         */
        private static class GenerationCache {
            private final int generation;
            private final CopyOnWriteHashMap<Integer, String> targets = new CopyOnWriteHashMap<>();

            GenerationCache(int generation) {
                this.generation = generation;
            }

            public int generation() { return this.generation; }

            public String get(Integer index) {
                return targets.get(index);
            }
            public void put(Integer index, String target) {
                targets.put(index, target);
            }
        }

        private final AtomicReference<GenerationCache> generationCache = new AtomicReference<>(null);

        TargetCachingSlobrokHostFetcher(SlobrokHostPatternGenerator patternGenerator, ExternalSlobrokPolicy policy) {
            super(patternGenerator, policy);
        }

        @Override
        public String getTargetSpec(Integer distributor, RoutingContext context) {
            GenerationCache cache = generationCache.get();
            int currentGeneration = getMirror(context).updates();
            // The below code might race with other threads during a generation change. That is OK, as the cache
            // is thread safe and will quickly converge to a stable state for the new generation.
            if (cache == null || currentGeneration != cache.generation()) {
                cache = new GenerationCache(currentGeneration);
                generationCache.set(cache);
            }
            if (distributor != null) {
                return cachingGetTargetSpec(distributor, context, cache);
            }
            // Wildcard lookup case. Must not be cached.
            return super.getTargetSpec(null, context);
        }

        private String cachingGetTargetSpec(Integer distributor, RoutingContext context, GenerationCache cache) {
            String cachedTarget = cache.get(distributor);
            if (cachedTarget != null) {
                return cachedTarget;
            }
            // Mirror _may_ be at a higher version if we race with generation read, but that is OK since
            // we'll either way get the most up-to-date mapping and the cache will be invalidated on the
            // next invocation.
            String resolvedTarget = super.getTargetSpec(distributor, context);
            cache.put(distributor, resolvedTarget);
            return resolvedTarget;
        }

    }

    /** Class parsing the semicolon separated parameter string and exposes the appropriate value to the policy. */
    public static class Parameters {
        protected final String clusterName;
        protected final String distributionConfigId;
        protected final SlobrokHostPatternGenerator slobrokHostPatternGenerator;

        public Parameters(Map<String, String> params) {
            clusterName = params.get("cluster");
            distributionConfigId = params.get("clusterconfigid");
            slobrokHostPatternGenerator = createPatternGenerator();
            if (clusterName == null) throw new IllegalArgumentException("Required parameter cluster with clustername not set");
        }

        public String getDistributionConfigId() {
            return (distributionConfigId == null ? "storage/cluster." + clusterName : distributionConfigId);
        }
        public String getClusterName() {
            return clusterName;
        }
        public SlobrokHostPatternGenerator createPatternGenerator() {
            return new SlobrokHostPatternGenerator(getClusterName());
        }
        public HostFetcher createHostFetcher(ExternalSlobrokPolicy policy) {
            return new TargetCachingSlobrokHostFetcher(slobrokHostPatternGenerator, policy);
        }
        public Distribution createDistribution(ExternalSlobrokPolicy policy) {
            return (policy.configSources != null ?
                    new Distribution(getDistributionConfigId(), new ConfigSourceSet(policy.configSources))
                  : new Distribution(getDistributionConfigId()));
        }

        /**
         * When we have gotten this amount of failures from a node (Any kind of failures). We try to send to a random other node, just to see if the
         * failure was related to node being bad. (Hard to detect from failure)
         */
        int getAttemptRandomOnFailuresLimit() { return 5; }

        /**
         * If we receive more than this number of wrong distribution replies with old cluster states, we throw the current cached state and takes the
         * old one. This guards us against version resets.
         */
        int maxOldClusterStatesSeenBeforeThrowingCachedState() { return 20; }

        /**
         * When getting new cluster states we update good nodes. If we have more than this percentage of up nodes, we send to up nodes instead of totally random.
         * (To avoid hitting trashing bad nodes still in slobrok)
         */
        int getRequiredUpPercentageToSendToKnownGoodNodes() { return 60; }
    }

    /** Helper class to get the bucket identifier of a message. */
    public static class BucketIdCalculator {
        private static final BucketIdFactory factory = new BucketIdFactory();

        private BucketId getBucketId(Message msg) {
            switch (msg.getType()) {
                case DocumentProtocol.MESSAGE_PUTDOCUMENT:         return factory.getBucketId(((PutDocumentMessage)msg).getDocumentPut().getDocument().getId());
                case DocumentProtocol.MESSAGE_GETDOCUMENT:         return factory.getBucketId(((GetDocumentMessage)msg).getDocumentId());
                case DocumentProtocol.MESSAGE_REMOVEDOCUMENT:      return factory.getBucketId(((RemoveDocumentMessage)msg).getDocumentId());
                case DocumentProtocol.MESSAGE_UPDATEDOCUMENT:      return factory.getBucketId(((UpdateDocumentMessage)msg).getDocumentUpdate().getId());
                case DocumentProtocol.MESSAGE_GETBUCKETLIST:       return ((GetBucketListMessage)msg).getBucketId();
                case DocumentProtocol.MESSAGE_STATBUCKET:          return ((StatBucketMessage)msg).getBucketId();
                case DocumentProtocol.MESSAGE_CREATEVISITOR:       return ((CreateVisitorMessage)msg).getBuckets().get(0);
                case DocumentProtocol.MESSAGE_REMOVELOCATION:      return ((RemoveLocationMessage)msg).getBucketId();
                default:
                    log.log(LogLevel.ERROR, "Message type '" + msg.getType() + "' not supported.");
                    return null;
            }
        }

        BucketId handleBucketIdCalculation(RoutingContext context) {
            BucketId id = getBucketId(context.getMessage());
            if (id == null || id.getRawId() == 0) {
                Reply reply = new EmptyReply();
                reply.addError(new Error(ErrorCode.APP_FATAL_ERROR, "No bucket id available in message."));
                context.setReply(reply);
            }
            return id;
        }
    }

    /** Class handling the logic of picking a distributor */
    public static class DistributorSelectionLogic {
        /** Class that tracks a failure of a given type per node. */
        static class InstabilityChecker {
            private List<Integer> nodeFailures = new ArrayList<>();
            private int failureLimit;

            InstabilityChecker(int failureLimit) { this.failureLimit = failureLimit; }

            boolean tooManyFailures(int nodeIndex) {
                if (nodeFailures.size() > nodeIndex && nodeFailures.get(nodeIndex) > failureLimit) {
                    nodeFailures.set(nodeIndex, 0);
                    return true;
                } else {
                    return false;
                }
            }

            void addFailure(Integer calculatedDistributor) {
                while (nodeFailures.size() <= calculatedDistributor) nodeFailures.add(0);
                nodeFailures.set(calculatedDistributor, nodeFailures.get(calculatedDistributor) + 1);
            }
        }
        /** Message context class. Contains data we want to inspect about a request at reply time. */
        private static class MessageContext {
            Integer calculatedDistributor;
            ClusterState usedState;

            MessageContext(ClusterState usedState) { this.usedState = usedState; }

            public String toString() {
                return "Context(Distributor " + calculatedDistributor +
                       ", state version " + usedState.getVersion() + ")";
            }
        }

        private final HostFetcher hostFetcher;
        private final Distribution distribution;
        private final InstabilityChecker persistentFailureChecker;
        private AtomicReference<ClusterState> safeCachedClusterState = new AtomicReference<>(null);
        private final AtomicInteger oldClusterVersionGottenCount = new AtomicInteger(0);
        private final int maxOldClusterVersionBeforeSendingRandom; // Reset cluster version protection

        DistributorSelectionLogic(Parameters params, ExternalSlobrokPolicy policy) {
            this.hostFetcher = params.createHostFetcher(policy);
            this.hostFetcher.setRequiredUpPercentageToSendToKnownGoodNodes(params.getRequiredUpPercentageToSendToKnownGoodNodes());
            this.distribution = params.createDistribution(policy);
            persistentFailureChecker = new InstabilityChecker(params.getAttemptRandomOnFailuresLimit());
            maxOldClusterVersionBeforeSendingRandom = params.maxOldClusterStatesSeenBeforeThrowingCachedState();
        }

        public void destroy() {
            hostFetcher.close();
            distribution.close();
        }

        String getTargetSpec(RoutingContext context, BucketId bucketId) {
            String sendRandomReason = null;
            ClusterState cachedClusterState = safeCachedClusterState.get();
            MessageContext messageContext = new MessageContext(cachedClusterState);
            context.setContext(messageContext);
            if (cachedClusterState != null) { // If we have a cached cluster state (regular case), we use that to calculate correct node.
                try{
                    Integer target = distribution.getIdealDistributorNode(cachedClusterState, bucketId, owningBucketStates);
                    // If we have had too many failures towards existing node, reset failure count and send to random
                    if (persistentFailureChecker.tooManyFailures(target)) {
                        sendRandomReason = "Too many failures detected versus distributor " + target + ". Sending to random instead of using cached state.";
                        target = null;
                    }
                    // If we have found a target, and the target exists in slobrok, send to it.
                    if (target != null) {
                        messageContext.calculatedDistributor = target;
                        String targetSpec = hostFetcher.getTargetSpec(target, context);
                        if (targetSpec != null) {
                            if (context.shouldTrace(1)) {
                                context.trace(1, "Using distributor " + messageContext.calculatedDistributor + " for " +
                                        bucketId + " as our state version is " + cachedClusterState.getVersion());
                            }
                            messageContext.usedState = cachedClusterState;
                            return targetSpec;
                        } else {
                            sendRandomReason = "Want to use distributor " + messageContext.calculatedDistributor + " but it is not in slobrok. Sending to random.";
                            log.log(LogLevel.DEBUG, "Target distributor is not in slobrok");
                        }
                    }
                } catch (Distribution.TooFewBucketBitsInUseException e) {
                    Reply reply = new WrongDistributionReply(cachedClusterState.toString(true));
                    reply.addError(new Error(DocumentProtocol.ERROR_WRONG_DISTRIBUTION,
                                             "Too few distribution bits used for given cluster state"));
                    context.setReply(reply);
                    return null;
                } catch (Distribution.NoDistributorsAvailableException e) {
                    log.log(LogLevel.DEBUG, "No distributors available; clearing cluster state");
                    safeCachedClusterState.set(null);
                    sendRandomReason = "No distributors available. Sending to random distributor.";
                }
            } else {
                sendRandomReason = "No cluster state cached. Sending to random distributor.";
            }
            if (context.shouldTrace(1)) {
                context.trace(1, sendRandomReason != null ? sendRandomReason : "Sending to random distributor for unknown reason");
            }
            return hostFetcher.getRandomTargetSpec(context);
        }

        private static Optional<ClusterState> clusterStateFromReply(final WrongDistributionReply reply) {
            try {
                return Optional.of(new ClusterState(reply.getSystemState()));
            } catch (Exception e) {
                reply.getTrace().trace(1, "Error when parsing system state string " + reply.getSystemState());
                return Optional.empty();
            }
        }

        void handleWrongDistribution(WrongDistributionReply reply, RoutingContext routingContext) {
            final MessageContext context = (MessageContext) routingContext.getContext();
            final Optional<ClusterState> replyState = clusterStateFromReply(reply);
            if (!replyState.isPresent()) {
                return;
            }
            final ClusterState newState = replyState.get();
            resetCachedStateIfClusterStateVersionLikelyRolledBack(newState);
            markReplyAsImmediateRetryIfNewStateObserved(reply, context, newState);

            if (context.calculatedDistributor == null) {
                traceReplyFromRandomDistributor(reply, newState);
            } else {
                traceReplyFromSpecificDistributor(reply, context, newState);
            }
            updateCachedRoutingStateFromWrongDistribution(context, newState);
        }

        private void updateCachedRoutingStateFromWrongDistribution(MessageContext context, ClusterState newState) {
            ClusterState cachedClusterState = safeCachedClusterState.get();
            if (cachedClusterState == null || newState.getVersion() >= cachedClusterState.getVersion()) {
                safeCachedClusterState.set(newState);
                if (newState.getClusterState().equals(State.UP)) {
                    hostFetcher.updateValidTargets(newState);
                }
            } else if (newState.getVersion() + 2000000000 < cachedClusterState.getVersion()) {
                safeCachedClusterState.set(null);
            } else if (context.calculatedDistributor != null) {
                persistentFailureChecker.addFailure(context.calculatedDistributor);
            }
        }

        private void traceReplyFromSpecificDistributor(WrongDistributionReply reply, MessageContext context, ClusterState newState) {
            if (context.usedState == null) {
                String msg = "Used state must be set as distributor is calculated. Bug.";
                reply.getTrace().trace(1, msg);
                log.log(LogLevel.ERROR, msg);
            } else if (newState.getVersion() == context.usedState.getVersion()) {
                String msg = "Message sent to distributor " + context.calculatedDistributor +
                             " retrieved cluster state version " + newState.getVersion() +
                             " which was the state we used to calculate distributor as target last time.";
                reply.getTrace().trace(1, msg);
                // Client load can be rejected towards distributors even with a matching cluster state version.
                // This usually happens during a node fail-over transition, where the target distributor will
                // reject an operation bound to a particular bucket if it does not own the bucket in _both_
                // the current and the next (transition target) state. Since it can happen during normal operation
                // and will happen per client operation, we keep this as debug level to prevent spamming the logs.
                log.log(LogLevel.DEBUG, msg);
            } else if (newState.getVersion() > context.usedState.getVersion()) {
                if (reply.getTrace().shouldTrace(1)) {
                    reply.getTrace().trace(1, "Message sent to distributor " + context.calculatedDistributor +
                            " updated cluster state from version " + context.usedState.getVersion() +
                            " to " + newState.getVersion());
                }
            } else {
                if (reply.getTrace().shouldTrace(1)) {
                    reply.getTrace().trace(1, "Message sent to distributor " + context.calculatedDistributor +
                            " returned older cluster state version " + newState.getVersion());
                }
            }
        }

        private void resetCachedStateIfClusterStateVersionLikelyRolledBack(ClusterState newState) {
            ClusterState cachedClusterState = safeCachedClusterState.get();
            if (cachedClusterState != null && cachedClusterState.getVersion() > newState.getVersion()) {
                if (oldClusterVersionGottenCount.incrementAndGet() >= maxOldClusterVersionBeforeSendingRandom) {
                    oldClusterVersionGottenCount.set(0);
                    safeCachedClusterState.set(null);
                }
            }
        }

        private void markReplyAsImmediateRetryIfNewStateObserved(WrongDistributionReply reply, MessageContext context, ClusterState newState) {
            if (context.usedState != null && newState.getVersion() <= context.usedState.getVersion()) {
                if (reply.getRetryDelay() <= 0.0) {
                    reply.setRetryDelay(-1);
                }
            } else {
                if (reply.getRetryDelay() <= 0.0) {
                    reply.setRetryDelay(0);
                }
            }
        }

        private void traceReplyFromRandomDistributor(WrongDistributionReply reply, ClusterState newState) {
            if (!reply.getTrace().shouldTrace(1)) {
                return;
            }
            ClusterState cachedClusterState = safeCachedClusterState.get();
            if (cachedClusterState == null) {
                reply.getTrace().trace(1, "Message sent to * with no previous state, received version " + newState.getVersion());
            } else if (newState.getVersion() == cachedClusterState.getVersion()) {
                reply.getTrace().trace(1, "Message sent to * found that cluster state version " + newState.getVersion() + " was correct.");
            } else if (newState.getVersion() > cachedClusterState.getVersion()) {
                reply.getTrace().trace(1, "Message sent to * updated cluster state to version " + newState.getVersion());
            } else {
                reply.getTrace().trace(1, "Message sent to * retrieved older cluster state version " + newState.getVersion());
            }
        }

        void handleErrorReply(Reply reply, Object untypedContext) {
            MessageContext messageContext = (MessageContext) untypedContext;
            if (messageContext.calculatedDistributor != null) {
                persistentFailureChecker.addFailure(messageContext.calculatedDistributor);
                if (reply.getTrace().shouldTrace(1)) {
                    reply.getTrace().trace(1, "Failed with " + messageContext.toString());
                }
            }
        }
    }

    private final BucketIdCalculator bucketIdCalculator = new BucketIdCalculator();
    private DistributorSelectionLogic distributorSelectionLogic = null;
    private Parameters parameters;

    /** Constructor used in production. */
    public StoragePolicy(String param) {
        this(parse(param));
    }

    public StoragePolicy(Map<String, String> params) {
        this(new Parameters(params), params);
    }

    /** Constructor specifying a bit more in detail, so we can override what needs to be overridden in tests */
    public StoragePolicy(Parameters p, Map<String, String> params) {
        super(params);
        parameters = p;
    }

    @Override
    public void init() {
        super.init();
        this.distributorSelectionLogic = new DistributorSelectionLogic(parameters, this);
    }

    @Override
    public void doSelect(RoutingContext context) {
        if (context.shouldTrace(1)) {
            context.trace(1, "Selecting route");
        }

        BucketId bucketId = bucketIdCalculator.handleBucketIdCalculation(context);
        if (context.hasReply()) return;

        String targetSpec = distributorSelectionLogic.getTargetSpec(context, bucketId);
        if (context.hasReply()) return;
        if (targetSpec != null) {
            Route route = new Route(context.getRoute());
            route.setHop(0, new Hop().addDirective(new VerbatimDirective(targetSpec)));
            context.addChild(route);
        } else {
            context.setError(ErrorCode.NO_ADDRESS_FOR_SERVICE,
                             "Could not resolve any distributors to send to in cluster " + parameters.clusterName);
        }
    }

    @Override
    public void merge(RoutingContext context) {
        RoutingNodeIterator it = context.getChildIterator();
        Reply reply = (it.hasReply()) ? it.removeReply() : context.getReply();
        if (reply == null) {
            reply = new EmptyReply();
            reply.addError(new Error(ErrorCode.NO_ADDRESS_FOR_SERVICE,
                    "No reply in any children, nor in the routing context: " + context));
        }

        if (reply instanceof WrongDistributionReply) {
            distributorSelectionLogic.handleWrongDistribution((WrongDistributionReply) reply, context);
        } else if (reply.hasErrors()) {
            distributorSelectionLogic.handleErrorReply(reply, context.getContext());
        } else if (reply instanceof WriteDocumentReply) {
            if (context.shouldTrace(9)) {
                context.trace(9, "Modification timestamp: " + ((WriteDocumentReply)reply).getHighestModificationTimestamp());
            }
        }
        context.setReply(reply);
    }

    @Override
    public void destroy() {
        distributorSelectionLogic.destroy();
    }
}
