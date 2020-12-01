// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::MergeThrottler
 * @ingroup storageserver
 *
 * @brief Throttler and forwarder of merge commands
 */
#pragma once

#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/distributor/messageguard.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/document/bucket/bucket.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/messagebus/staticthrottlepolicy.h>
#include <vespa/metrics/metricset.h>
#include <vespa/metrics/summetric.h>
#include <vespa/metrics/countmetric.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/metrictimer.h>
#include <vespa/config/config.h>
#include <chrono>

namespace storage {

class AbortBucketOperationsCommand;

class MergeThrottler : public framework::Runnable,
                       public StorageLink,
                       public framework::HtmlStatusReporter,
                       private config::IFetcherCallback<vespa::config::content::core::StorServerConfig>
{
public:
    class MergeFailureMetrics : public metrics::MetricSet {
    public:
        metrics::SumMetric<metrics::LongCountMetric> sum;
        metrics::LongCountMetric notready;
        metrics::LongCountMetric timeout;
        metrics::LongCountMetric aborted;
        metrics::LongCountMetric wrongdistribution;
        metrics::LongCountMetric bucketnotfound;
        metrics::LongCountMetric busy;
        metrics::LongCountMetric exists;
        metrics::LongCountMetric rejected;
        metrics::LongCountMetric other;

        MergeFailureMetrics(metrics::MetricSet* owner);
        ~MergeFailureMetrics() override;
    };

    class MergeOperationMetrics : public metrics::MetricSet {
    public:
        metrics::LongCountMetric ok;
        MergeFailureMetrics failures;

        MergeOperationMetrics(const std::string& name, metrics::MetricSet* owner);
        ~MergeOperationMetrics();
    };

    class Metrics : public metrics::MetricSet {
    public:
        metrics::DoubleAverageMetric averageQueueWaitingTime;
        metrics::LongCountMetric bounced_due_to_back_pressure;
        MergeOperationMetrics chaining;
        MergeOperationMetrics local;

        Metrics(metrics::MetricSet* owner = 0);
        ~Metrics();
    };

private:
    // TODO: make PQ with stable ordering into own, generic class
    template <class MessageType>
    struct StablePriorityOrderingWrapper {
        MessageType _msg;
        metrics::MetricTimer _startTimer;
        uint64_t _sequence;

        StablePriorityOrderingWrapper(const MessageType& msg, uint64_t sequence)
            : _msg(msg), _startTimer(), _sequence(sequence)
        {
        }

        bool operator==(const StablePriorityOrderingWrapper& other) const {
            return (*_msg == *other._msg
                    && _sequence == other._sequence);
        }

        bool operator<(const StablePriorityOrderingWrapper& other) const {
            if (_msg->getPriority() < other._msg->getPriority()) {
                return true;
            }
            return (_sequence < other._sequence);
        }
    };

    struct ChainedMergeState {
        api::StorageMessage::SP _cmd;
        std::string _cmdString; // For being able to print message even when we don't own it
        uint64_t _clusterStateVersion;
        bool _inCycle;
        bool _executingLocally;
        bool _unwinding;
        bool _cycleBroken;
        bool _aborted;

        ChainedMergeState();
        ChainedMergeState(const api::StorageMessage::SP& cmd, bool executing = false);
        ~ChainedMergeState();
        // Use default copy-constructor/assignment operator

        bool isExecutingLocally() const { return _executingLocally; }
        void setExecutingLocally(bool execLocally) { _executingLocally = execLocally; }

        const api::StorageMessage::SP& getMergeCmd() const { return _cmd; }
        void setMergeCmd(const api::StorageMessage::SP& cmd) {
            _cmd = cmd;
            if (cmd.get()) {
                _cmdString = cmd->toString();
            }
        }

        bool isInCycle() const { return _inCycle; }
        void setInCycle(bool inCycle) { _inCycle = inCycle; }

        bool isUnwinding() const { return _unwinding; }
        void setUnwinding(bool unwinding) { _unwinding = unwinding; }

        bool isCycleBroken() const { return _cycleBroken; }
        void setCycleBroken(bool cycleBroken) { _cycleBroken = cycleBroken; }

        bool isAborted() const { return _aborted; }
        void setAborted(bool aborted) { _aborted = aborted; }

        const std::string& getMergeCmdString() const { return _cmdString; }
    };

    typedef std::map<document::Bucket, ChainedMergeState> ActiveMergeMap;

    // Use a set rather than a priority_queue, since we want to be
    // able to iterate over the collection during status rendering
    typedef std::set<
        StablePriorityOrderingWrapper<api::StorageMessage::SP>
    > MergePriorityQueue;

    enum RendezvousState {
        RENDEZVOUS_NONE,
        RENDEZVOUS_REQUESTED,
        RENDEZVOUS_ESTABLISHED,
        RENDEZVOUS_RELEASED
    };

    ActiveMergeMap _merges;
    MergePriorityQueue _queue;
    std::size_t _maxQueueSize;
    mbus::StaticThrottlePolicy::UP _throttlePolicy;
    uint64_t _queueSequence; // TODO: move into a stable priority queue class
    mutable std::mutex _messageLock;
    std::condition_variable _messageCond;
    mutable std::mutex _stateLock;
    config::ConfigFetcher _configFetcher;
    // Messages pending to be processed by the worker thread
    std::vector<api::StorageMessage::SP> _messagesDown;
    std::vector<api::StorageMessage::SP> _messagesUp;
    std::unique_ptr<Metrics> _metrics;
    StorageComponent _component;
    framework::Thread::UP _thread;
    RendezvousState _rendezvous;
    mutable std::chrono::steady_clock::time_point _throttle_until_time;
    std::chrono::steady_clock::duration _backpressure_duration;
    bool _closing;
public:
    /**
     * windowSizeIncrement used for allowing unit tests to start out with more
     * than 1 as their window size.
     */
    MergeThrottler(const config::ConfigUri & configUri, StorageComponentRegister&);
    ~MergeThrottler() override;

    /** Implements document::Runnable::run */
    void run(framework::ThreadHandle&) override;

    void onOpen() override;
    void onClose() override;
    void onFlush(bool downwards) override;
    bool onUp(const std::shared_ptr<api::StorageMessage>& msg) override;
    bool onDown(const std::shared_ptr<api::StorageMessage>& msg) override;

    bool onSetSystemState(const std::shared_ptr<api::SetSystemStateCommand>& stateCmd) override;

    /*
     * When invoked, merges to the node will be BUSY-bounced by the throttler
     * for a configurable period of time instead of being processed.
     *
     * Thread safe, but must not be called if _stateLock is already held, or
     * deadlock will occur.
     */
    void apply_timed_backpressure();
    bool backpressure_mode_active() const;

    // For unit testing only
    const ActiveMergeMap& getActiveMerges() const { return _merges; }
    // For unit testing only
    const MergePriorityQueue& getMergeQueue() const { return _queue; }
    // For unit testing only
    const mbus::StaticThrottlePolicy& getThrottlePolicy() const { return *_throttlePolicy; }
    mbus::StaticThrottlePolicy& getThrottlePolicy() { return *_throttlePolicy; }
    // For unit testing only
    std::mutex & getMonitor() { return _messageLock; }
    std::mutex & getStateLock() { return _stateLock; }

    Metrics& getMetrics() { return *_metrics; }
    std::size_t getMaxQueueSize() const { return _maxQueueSize; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;
private:
    friend class ThreadRendezvousGuard; // impl in .cpp file

    // Simple helper class for centralizing chaining logic
    struct MergeNodeSequence {
        const api::MergeBucketCommand& _cmd;
        std::vector<api::MergeBucketCommand::Node> _sortedNodes;
        std::size_t _sortedIndex; // Index of current storage node in the sorted node sequence
        const uint16_t _thisIndex; // Index of the current storage node

        MergeNodeSequence(const api::MergeBucketCommand& cmd, uint16_t thisIndex);

        std::size_t getSortedIndex() const { return _sortedIndex; }
        const std::vector<api::MergeBucketCommand::Node>& getSortedNodes() const {
            return _sortedNodes;
        }
        bool isIndexUnknown() const {
            return (_sortedIndex == std::numeric_limits<std::size_t>::max());
        }
        /**
         * This node is the merge executor if it's the first element in the
         * _unsorted_ node sequence.
         */
        bool isMergeExecutor() const {
            return (_cmd.getNodes()[0].index == _thisIndex);
        }
        uint16_t getExecutorNodeIndex() const{
            return _cmd.getNodes()[0].index;
        }
        bool isLastNode() const {
            return (_sortedIndex == _sortedNodes.size() - 1);
        }
        bool chainContainsIndex(uint16_t idx) const;
        uint16_t getThisNodeIndex() const { return _thisIndex; }
        /**
         * Gets node to forward to in strictly increasing order.
         */
        uint16_t getNextNodeInChain() const;

        /**
         * Returns true iff the chain vector (which is implicitly sorted)
         * pairwise compares equally to the vector of sorted node indices
         */
        bool isChainCompleted() const;
        std::string getSequenceString() const;
    };

    /**
     * Callback method for config system (IFetcherCallback)
     */
    void configure(std::unique_ptr<vespa::config::content::core::StorServerConfig> newConfig) override;

    // NOTE: unless explicitly specified, all the below functions require
    // _sync lock to be held upon call (usually implicitly via MessageGuard)

    void handleMessageDown(const std::shared_ptr<api::StorageMessage>& msg, MessageGuard& msgGuard);
    void handleMessageUp(const std::shared_ptr<api::StorageMessage>& msg, MessageGuard& msgGuard);

    /**
     * Handle the receival of MergeBucketReply, be it from another node
     * or from the persistence layer on the current node itself. In the
     * case of the former, fromPersistenceLayer must be false, since we have
     * to generate a new reply to pass back to the unwind chain. In
     * case of the latter, fromPersistenceLayer must be true since the
     * reply from the persistence layer will be automatically sent
     * back in the chain.
     */
    void processMergeReply(
            const std::shared_ptr<api::StorageMessage>& msg,
            bool fromPersistenceLayer,
            MessageGuard& msgGuard);

    /**
     * Validate that the merge command is consistent with our current
     * state.
     * @return true if message is valid and may be further processed.
     * If false is returned, a rejection reply will have been sent up
     * on the message guard.
     */
    bool validateNewMerge(
            const api::MergeBucketCommand& mergeCmd,
            const MergeNodeSequence& nodeSeq,
            MessageGuard& msgGuard) const;
    /**
     * Register a new merge bucket command with the internal state and
     * either forward or execute it, depending on where the current node
     * is located in the merge chain.
     *
     * Precondition: no existing merge state exists for msg's bucketid.
     */
    void processNewMergeCommand(const api::StorageMessage::SP& msg, MessageGuard& msgGuard);

    /**
     * Precondition: an existing merge state exists for msg's bucketid.
     * @return true if message was handled, false otherwise (see onUp/onDown).
     */
    bool processCycledMergeCommand(const api::StorageMessage::SP& msg, MessageGuard& msgGuard);

    /**
     * Forwards the given MergeBucketCommand to the storage node given
     * by nodeIndex. New forwarded message will inherit mergeCmd's priority.
     * The current node's index will be added to the end of the merge
     * chain vector.
     */
    void forwardCommandToNode(
            const api::MergeBucketCommand& mergeCmd,
            uint16_t nodeIndex,
            MessageGuard& msgGuard);

    void removeActiveMerge(ActiveMergeMap::iterator);

    /**
     * Gets (and pops) the highest priority merge waiting in the queue,
     * if one exists.
     * @return Highest priority waiting merge or null SP if queue is empty
     */
    api::StorageMessage::SP getNextQueuedMerge();
    void enqueueMerge(const api::StorageMessage::SP& msg, MessageGuard& msgGuard);

    /**
     * @return true if throttle policy says at least one additional
     * merge can be processed.
     */
    bool canProcessNewMerge() const;

    bool merge_is_backpressure_throttled(const api::MergeBucketCommand& cmd) const;
    void bounce_backpressure_throttled_merge(const api::MergeBucketCommand& cmd, MessageGuard& guard);
    bool merge_has_this_node_as_source_only_node(const api::MergeBucketCommand& cmd) const;
    bool backpressure_mode_active_no_lock() const;
    void backpressure_bounce_all_queued_merges(MessageGuard& guard);

    void sendReply(const api::MergeBucketCommand& cmd,
                   const api::ReturnCode& result,
                   MessageGuard& msgGuard,
                   MergeOperationMetrics& metrics) const;

    /**
     * @return true if a merge for msg's bucketid is already registered
     * in the internal merge throttler state.
     */
    bool isMergeAlreadyKnown(const api::StorageMessage::SP& msg) const;

    bool rejectMergeIfOutdated(
            const api::StorageMessage::SP& msg,
            uint32_t rejectLessThanVersion,
            MessageGuard& msgGuard) const;

    /**
     * Immediately reject all queued merges whose cluster state version is
     * less than that of rejectLessThanVersion
     */
    void rejectOutdatedQueuedMerges(MessageGuard& msgGuard, uint32_t rejectLessThanVersion);
    bool attemptProcessNextQueuedMerge(MessageGuard& msgGuard);
    bool processQueuedMerges(MessageGuard& msgGuard);
    void handleRendezvous(std::unique_lock<std::mutex> & guard, std::condition_variable & cond);
    void rendezvousWithWorkerThread(std::unique_lock<std::mutex> & guard, std::condition_variable & cond);
    void releaseWorkerThreadRendezvous(std::unique_lock<std::mutex> & guard, std::condition_variable & cond);
    bool isDiffCommand(const api::StorageMessage& msg) const;
    bool isMergeCommand(const api::StorageMessage& msg) const;
    bool isMergeReply(const api::StorageMessage& msg) const;
    bool bucketIsUnknownOrAborted(const document::Bucket& bucket) const;

    std::shared_ptr<api::StorageMessage> makeAbortReply(
            api::StorageCommand& cmd,
            vespalib::stringref reason) const;

    void handleOutdatedMerges(const api::SetSystemStateCommand&);
    void rejectOperationsInThreadQueue(MessageGuard&, uint32_t minimumStateVersion);
    void markActiveMergesAsAborted(uint32_t minimumStateVersion);

    // const function, but metrics are mutable
    void updateOperationMetrics(
            const api::ReturnCode& result,
            MergeOperationMetrics& metrics) const;
};

} // namespace storage
