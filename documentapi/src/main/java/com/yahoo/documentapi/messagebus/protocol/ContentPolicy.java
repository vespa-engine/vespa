// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.jrt.slobrok.api.Mirror;
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
import com.yahoo.vespa.config.content.DistributionConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routing policy to determine which distributor in a content cluster to send data to.
 * Using different key=value parameters separated by semicolon (";"), the user can control which cluster to send to.
 *
 * cluster=[clusterName] (Mandatory, determines the cluster name)
 * config=[config] (Optional, a comma separated list of config servers to use. Used to talk to clusters not defined in this vespa application)
 * clusterconfigid=[id] (Optional, use given config id for distribution instead of default)
 *
 * @author Haakon Humberset
 */
public class ContentPolicy extends SlobrokPolicy {

    private static final Logger log = Logger.getLogger(ContentPolicy.class.getName());
    public static final String owningBucketStates = "uim";
    private static final String upStates = "ui";

    /** This class merely generates a slobrok host pattern for a given distributor. */
    static class SlobrokHostPatternGenerator {

        private final String base;

        SlobrokHostPatternGenerator(String clusterName) {
            this.base = "storage/cluster." + clusterName + "/distributor/";
        }

        /**
         * Find host pattern of the hosts that are valid targets for this request.
         *
         * @param distributor Set to null if any distributor is valid target.
         */
        String getDistributorHostPattern(Integer distributor) {
            return base + (distributor == null ? "*" : distributor) + "/default";
        }

    }

    /** Helper class to match a host pattern with node to use. */
    public abstract static class HostFetcher {

        private static class Targets {
            private final List<Integer> list;
            private final int total;
            Targets() {
                this(Collections.emptyList(), 1);
            }
            Targets(List<Integer> list, int total) {
                this.list = list;
                this.total = total;
            }
        }

        private final int requiredUpPercentageToSendToKnownGoodNodes;
        private final AtomicReference<Targets> validTargets = new AtomicReference<>(new Targets());
        protected final Random randomizer = new Random(12345); // Use same randomizer each time to make unit testing easy.

        protected HostFetcher(int percent) {
            requiredUpPercentageToSendToKnownGoodNodes = percent;
        }

        void updateValidTargets(ClusterState state) {
            List<Integer> validRandomTargets = new ArrayList<>();
            for (int i=0; i<state.getNodeCount(NodeType.DISTRIBUTOR); ++i) {
                if (state.getNodeState(new Node(NodeType.DISTRIBUTOR, i)).getState().oneOf(upStates)) validRandomTargets.add(i);
            }
            validTargets.set(new Targets(new CopyOnWriteArrayList<>(validRandomTargets), state.getNodeCount(NodeType.DISTRIBUTOR)));
        }
        public abstract String getTargetSpec(Integer distributor, RoutingContext context);
        String getRandomTargetSpec(RoutingContext context) {
            Targets targets = validTargets.get();
            // Try to use list of random targets, if at least X % of the nodes are up
            while ((targets.total != 0) &&
                   (100 * targets.list.size() / targets.total >= requiredUpPercentageToSendToKnownGoodNodes))
            {
                int randIndex = randomizer.nextInt(targets.list.size());
                String targetSpec = getTargetSpec(targets.list.get(randIndex), context);
                if (targetSpec != null) {
                    context.trace(3, "Sending to random node seen up in cluster state");
                    return targetSpec;
                }
                targets.list.remove(randIndex);
            }
            context.trace(3, "Too few nodes seen up in state. Sending totally random.");
            return getTargetSpec(null, context);
        }
        public void close() {}
    }

    /** Host fetcher using a slobrok mirror to find the hosts. */
    public static class SlobrokHostFetcher extends HostFetcher {
        private final SlobrokHostPatternGenerator patternGenerator;
        private final SlobrokPolicy policy;

        SlobrokHostFetcher(SlobrokHostPatternGenerator patternGenerator, SlobrokPolicy policy, int percent) {
            super(percent);
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
                    return convertSlobrokNameToSessionName(arr.get(0).getSpecString());
                } else {
                    log.log(Level.WARNING, "Got " + arr.size() + " matches for a distributor.");
                }
            } else {
                return convertSlobrokNameToSessionName(arr.get(randomizer.nextInt(arr.size())).getSpecString());
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

        TargetCachingSlobrokHostFetcher(SlobrokHostPatternGenerator patternGenerator, SlobrokPolicy policy, int percent) {
            super(patternGenerator, policy, percent);
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
        protected final DistributionConfig distributionConfig;
        protected final SlobrokHostPatternGenerator slobrokHostPatternGenerator;

        public Parameters(Map<String, String> params) {
            this(params, null);
        }

        private Parameters(Map<String, String> params, DistributionConfig config) {
            clusterName = params.get("cluster");
            if (clusterName == null)
                throw new IllegalArgumentException("Required parameter 'cluster', the name of the content cluster, not set");
            distributionConfig = config;
            if (distributionConfig != null && distributionConfig.cluster(clusterName) == null)
                throw new IllegalArgumentException("Distribution config for cluster '" + clusterName + "' not found");
            distributionConfigId = params.get("clusterconfigid"); // TODO jonmv: remove
            slobrokHostPatternGenerator = createPatternGenerator();
        }

        private String getDistributionConfigId() {
            return distributionConfigId == null ? clusterName : distributionConfigId;
        }
        public String getClusterName() {
            return clusterName;
        }
        public SlobrokHostPatternGenerator createPatternGenerator() {
            return new SlobrokHostPatternGenerator(getClusterName());
        }
        public HostFetcher createHostFetcher(SlobrokPolicy policy, int percent) {
            return new TargetCachingSlobrokHostFetcher(slobrokHostPatternGenerator, policy, percent);
        }
        public Distribution createDistribution(SlobrokPolicy policy) {
            return distributionConfig == null ? new Distribution(getDistributionConfigId())
                                              : new Distribution(distributionConfig.cluster(clusterName));
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
                    log.log(Level.SEVERE, "Message type '" + msg.getType() + "' not supported.");
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
            private final List<Integer> nodeFailures = new CopyOnWriteArrayList<>();
            private final int failureLimit;

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
            final Integer calculatedDistributor;
            final ClusterState usedState;

            MessageContext(ClusterState usedState) {
                this(usedState, null);
            }
            MessageContext(ClusterState usedState, Integer calculatedDistributor) {
                this.calculatedDistributor = calculatedDistributor;
                this.usedState = usedState;
            }

            public String toString() {
                return "Context(Distributor " + calculatedDistributor +
                       ", state version " + usedState.getVersion() + ")";
            }
        }

        private final HostFetcher hostFetcher;
        private final Distribution distribution;
        private final InstabilityChecker persistentFailureChecker;
        private final AtomicReference<ClusterState> safeCachedClusterState = new AtomicReference<>(null);
        private final AtomicInteger oldClusterVersionGottenCount = new AtomicInteger(0);
        private final int maxOldClusterVersionBeforeSendingRandom; // Reset cluster version protection

        DistributorSelectionLogic(Parameters params, SlobrokPolicy policy) {
            try {
                hostFetcher = params.createHostFetcher(policy, params.getRequiredUpPercentageToSendToKnownGoodNodes());
                distribution = params.createDistribution(policy);
                persistentFailureChecker = new InstabilityChecker(params.getAttemptRandomOnFailuresLimit());
                maxOldClusterVersionBeforeSendingRandom = params.maxOldClusterStatesSeenBeforeThrowingCachedState();
            } catch (Throwable e) {
                destroy();
                throw e;
            }
        }

        public void destroy() {
            if (hostFetcher != null) {
                hostFetcher.close();
            }
            if (distribution != null) {
                distribution.close();
            }
        }

        String getTargetSpec(RoutingContext context, BucketId bucketId) {
            String sendRandomReason = null;
            ClusterState cachedClusterState = safeCachedClusterState.get();

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
                        context.setContext(new MessageContext(cachedClusterState, target));
                        String targetSpec = hostFetcher.getTargetSpec(target, context);
                        if (targetSpec != null) {
                            if (context.shouldTrace(1)) {
                                context.trace(1, "Using distributor " + target + " for " +
                                        bucketId + " as our state version is " + cachedClusterState.getVersion());
                            }
                            return targetSpec;
                        } else {
                            sendRandomReason = "Want to use distributor " + target + " but it is not in slobrok. Sending to random.";
                            log.log(Level.FINE, "Target distributor is not in slobrok");
                        }
                    } else {
                        context.setContext(new MessageContext(cachedClusterState));
                    }
                } catch (Distribution.TooFewBucketBitsInUseException e) {
                    Reply reply = new WrongDistributionReply(cachedClusterState.toString(true));
                    reply.addError(new Error(DocumentProtocol.ERROR_WRONG_DISTRIBUTION,
                                             "Too few distribution bits used for given cluster state"));
                    context.setReply(reply);
                    return null;
                } catch (Distribution.NoDistributorsAvailableException e) {
                    log.log(Level.FINE, "No distributors available; clearing cluster state");
                    safeCachedClusterState.set(null);
                    sendRandomReason = "No distributors available. Sending to random distributor.";
                    context.setContext(createRandomDistributorTargetContext());
                }
            } else {
                context.setContext(createRandomDistributorTargetContext());
                sendRandomReason = "No cluster state cached. Sending to random distributor.";
            }
            if (context.shouldTrace(1)) {
                context.trace(1, sendRandomReason != null ? sendRandomReason : "Sending to random distributor for unknown reason");
            }
            return hostFetcher.getRandomTargetSpec(context);
        }

        private static MessageContext createRandomDistributorTargetContext() {
            return new MessageContext(null);
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
                log.log(Level.SEVERE, msg);
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
                log.log(Level.FINE, msg);
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
    private final DistributorSelectionLogic distributorSelectionLogic;
    private final Parameters parameters;

    /** Constructor used in production. */
    public ContentPolicy(String param, DistributionConfig config) {
        this(new Parameters(parse(param), config));
    }

    /** Constructor specifying a bit more in detail, so we can override what needs to be overridden in tests */
    public ContentPolicy(Parameters p) {
        super();
        parameters = p;
        distributorSelectionLogic = new DistributorSelectionLogic(parameters, this);
    }

    @Override
    public void select(RoutingContext context) {
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
