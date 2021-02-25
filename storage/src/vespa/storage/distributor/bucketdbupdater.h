// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketlistmerger.h"
#include "messageguard.h"
#include "distributorcomponent.h"
#include "distributormessagesender.h"
#include "pendingclusterstate.h"
#include "operation_routing_snapshot.h"
#include "outdated_nodes_map.h"
#include <vespa/document/bucket/bucket.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/storageframework/generic/status/statusreporter.h>
#include <vespa/storageapi/messageapi/messagehandler.h>
#include <atomic>
#include <list>
#include <mutex>

namespace vespalib::xml {
class XmlOutputStream;
class XmlAttribute;
}

namespace storage::distributor {

class Distributor;
class BucketSpaceDistributionContext;

class BucketDBUpdater : public framework::StatusReporter,
                        public api::MessageHandler
{
public:
    using OutdatedNodesMap = dbtransition::OutdatedNodesMap;
    BucketDBUpdater(Distributor& owner,
                    DistributorBucketSpaceRepo& bucketSpaceRepo,
                    DistributorBucketSpaceRepo& readOnlyBucketSpaceRepo,
                    DistributorMessageSender& sender,
                    DistributorComponentRegister& compReg);
    ~BucketDBUpdater() override;

    void flush();
    const lib::ClusterState* pendingClusterStateOrNull(const document::BucketSpace&) const;
    void recheckBucketInfo(uint32_t nodeIdx, const document::Bucket& bucket);

    bool onSetSystemState(const std::shared_ptr<api::SetSystemStateCommand>& cmd) override;
    bool onActivateClusterStateVersion(const std::shared_ptr<api::ActivateClusterStateVersionCommand>& cmd) override;
    bool onRequestBucketInfoReply(const std::shared_ptr<api::RequestBucketInfoReply> & repl) override;
    bool onMergeBucketReply(const std::shared_ptr<api::MergeBucketReply>& reply) override;
    bool onNotifyBucketChange(const std::shared_ptr<api::NotifyBucketChangeCommand>&) override;
    void resendDelayedMessages();
    void storageDistributionChanged();

    vespalib::string reportXmlStatus(vespalib::xml::XmlOutputStream&, const framework::HttpUrlPath&) const;
    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream&, const framework::HttpUrlPath&) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const;
    DistributorComponent& getDistributorComponent() { return _distributorComponent; }

    /**
     * Returns whether the current PendingClusterState indicates that there has
     * been a transfer of bucket ownership amongst the distributors in the
     * cluster. This method only makes sense to call when _pendingClusterState
     * is active, such as from within a enableClusterState() call.
     */
    bool bucketOwnershipHasChanged() const {
        return ((_pendingClusterState.get() != nullptr)
                && _pendingClusterState->hasBucketOwnershipTransfer());
    }
    void set_stale_reads_enabled(bool enabled) noexcept {
        _stale_reads_enabled.store(enabled, std::memory_order_relaxed);
    }
    bool stale_reads_enabled() const noexcept {
        return _stale_reads_enabled.load(std::memory_order_relaxed);
    }

    OperationRoutingSnapshot read_snapshot_for_bucket(const document::Bucket&) const;
private:
    DistributorComponent _distributorComponent;
    class MergeReplyGuard {
    public:
        MergeReplyGuard(BucketDBUpdater& updater, const std::shared_ptr<api::MergeBucketReply>& reply)
            : _updater(updater), _reply(reply) {}

        ~MergeReplyGuard();

        // Used when we're flushing and simply want to drop the reply rather
        // than send it down
        void resetReply() { _reply.reset(); }
    private:
        BucketDBUpdater& _updater;
        std::shared_ptr<api::MergeBucketReply> _reply;
    };

    struct BucketRequest {
        BucketRequest()
            : targetNode(0), bucket(), timestamp(0) {};

        BucketRequest(uint16_t t, uint64_t currentTime, const document::Bucket& b,
                      const std::shared_ptr<MergeReplyGuard>& guard)
            : targetNode(t),
              bucket(b),
              timestamp(currentTime),
              _mergeReplyGuard(guard) {};

        void print_xml_tag(vespalib::xml::XmlOutputStream &xos, const vespalib::xml::XmlAttribute &timestampAttribute) const;
        uint16_t targetNode;
        document::Bucket bucket;
        uint64_t timestamp;

        std::shared_ptr<MergeReplyGuard> _mergeReplyGuard;
    };

    struct EnqueuedBucketRecheck {
        uint16_t node;
        document::Bucket bucket;

        EnqueuedBucketRecheck() : node(0), bucket() {}

        EnqueuedBucketRecheck(uint16_t _node, const document::Bucket& _bucket)
          : node(_node),
            bucket(_bucket)
        {}

        bool operator<(const EnqueuedBucketRecheck& o) const {
            if (node != o.node) {
                return node < o.node;
            }
            return bucket < o.bucket;
        }
        bool operator==(const EnqueuedBucketRecheck& o) const {
            return node == o.node && bucket == o.bucket;
        }
    };

    friend class DistributorTestUtil;
    // Only to be used by tests that want to ensure both the BucketDBUpdater _and_ the Distributor
    // components agree on the currently active cluster state bundle.
    // Transitively invokes Distributor::enableClusterStateBundle
    void simulate_cluster_state_bundle_activation(const lib::ClusterStateBundle& activated_state);

    bool shouldDeferStateEnabling() const noexcept;
    bool hasPendingClusterState() const;
    bool pendingClusterStateAccepted(const std::shared_ptr<api::RequestBucketInfoReply>& repl);
    bool processSingleBucketInfoReply(const std::shared_ptr<api::RequestBucketInfoReply>& repl);
    void handleSingleBucketInfoFailure(const std::shared_ptr<api::RequestBucketInfoReply>& repl,
                                       const BucketRequest& req);
    bool isPendingClusterStateCompleted() const;
    void processCompletedPendingClusterState();
    void activatePendingClusterState();
    void mergeBucketInfoWithDatabase(const std::shared_ptr<api::RequestBucketInfoReply>& repl,
                                     const BucketRequest& req);
    void convertBucketInfoToBucketList(const std::shared_ptr<api::RequestBucketInfoReply>& repl,
                                       uint16_t targetNode, BucketListMerger::BucketList& newList);
    void sendRequestBucketInfo(uint16_t node, const document::Bucket& bucket,
                               const std::shared_ptr<MergeReplyGuard>& mergeReply);
    void addBucketInfoForNode(const BucketDatabase::Entry& e, uint16_t node,
                              BucketListMerger::BucketList& existing) const;
    void ensureTransitionTimerStarted();
    void completeTransitionTimer();
    void clearReadOnlyBucketRepoDatabases();
    /**
     * Adds all buckets contained in the bucket database
     * that are either contained
     * in bucketId, or that bucketId is contained in, that have copies
     * on the given node.
     */
    void findRelatedBucketsInDatabase(uint16_t node, const document::Bucket& bucket,
                                      BucketListMerger::BucketList& existing);

    /**
       Updates the bucket database from the information generated by the given
       bucket list merger.
    */
    void updateDatabase(document::BucketSpace bucketSpace, uint16_t node, BucketListMerger& merger);

    void updateState(const lib::ClusterState& oldState, const lib::ClusterState& newState);

    void update_read_snapshot_before_db_pruning();
    void removeSuperfluousBuckets(const lib::ClusterStateBundle& newState,
                                  bool is_distribution_config_change);
    void update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state);
    void update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state);

    void replyToPreviousPendingClusterStateIfAny();
    void replyToActivationWithActualVersion(
            const api::ActivateClusterStateVersionCommand& cmd,
            uint32_t actualVersion);

    void enableCurrentClusterStateBundleInDistributor();
    void addCurrentStateToClusterStateHistory();
    void enqueueRecheckUntilPendingStateEnabled(uint16_t node, const document::Bucket&);
    void sendAllQueuedBucketRechecks();

    void maybe_inject_simulated_db_pruning_delay();
    void maybe_inject_simulated_db_merging_delay();

    /**
       Removes all copies of buckets that are on nodes that are down.
    */
    class MergingNodeRemover : public BucketDatabase::MergingProcessor {
    public:
        MergingNodeRemover(const lib::ClusterState& oldState,
                           const lib::ClusterState& s,
                           uint16_t localIndex,
                           const lib::Distribution& distribution,
                           const char* upStates,
                           bool track_non_owned_entries);
        ~MergingNodeRemover() override;

        Result merge(BucketDatabase::Merger&) override;
        void logRemove(const document::BucketId& bucketId, const char* msg) const;
        bool distributorOwnsBucket(const document::BucketId&) const;

        const std::vector<BucketDatabase::Entry>& getNonOwnedEntries() const noexcept {
            return _nonOwnedBuckets;
        }
    private:
        void setCopiesInEntry(BucketDatabase::Entry& e, const std::vector<BucketCopy>& copies) const;

        bool has_unavailable_nodes(const BucketDatabase::Entry&) const;
        bool storage_node_is_available(uint16_t index) const noexcept;

        const lib::ClusterState _oldState;
        const lib::ClusterState _state;
        std::vector<bool> _available_nodes;
        std::vector<BucketDatabase::Entry> _nonOwnedBuckets;
        size_t _removed_buckets;
        size_t _removed_documents;

        uint16_t _localIndex;
        const lib::Distribution& _distribution;
        const char* _upStates;
        bool _track_non_owned_entries;

        mutable uint64_t _cachedDecisionSuperbucket;
        mutable bool _cachedOwned;
    };

    std::deque<std::pair<framework::MilliSecTime, BucketRequest> > _delayedRequests;
    std::map<uint64_t, BucketRequest> _sentMessages;
    std::unique_ptr<PendingClusterState> _pendingClusterState;
    std::list<PendingClusterState::Summary> _history;
    DistributorMessageSender& _sender;
    std::set<EnqueuedBucketRecheck> _enqueuedRechecks;
    OutdatedNodesMap         _outdatedNodesMap;
    framework::MilliSecTimer _transitionTimer;
    std::atomic<bool> _stale_reads_enabled;
    using DistributionContexts = std::unordered_map<document::BucketSpace,
                                                    std::shared_ptr<BucketSpaceDistributionContext>,
                                                    document::BucketSpace::hash>;
    DistributionContexts _active_distribution_contexts;
    using DbGuards = std::unordered_map<document::BucketSpace,
                                        std::shared_ptr<BucketDatabase::ReadGuard>,
                                        document::BucketSpace::hash>;
    DbGuards _explicit_transition_read_guard;
    mutable std::mutex _distribution_context_mutex;
};

}
