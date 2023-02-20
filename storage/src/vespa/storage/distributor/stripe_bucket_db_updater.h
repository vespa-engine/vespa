// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketlistmerger.h"
#include "distributor_stripe_component.h"
#include "distributormessagesender.h"
#include "operation_routing_snapshot.h"
#include "outdated_nodes_map.h"
#include "pendingclusterstate.h"
#include "potential_data_loss_report.h"
#include <vespa/document/bucket/bucket.h>
#include <vespa/storage/common/message_guard.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/messageapi/messagehandler.h>
#include <vespa/storageframework/generic/status/statusreporter.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <atomic>
#include <list>
#include <mutex>

namespace vespalib::xml {
class XmlOutputStream;
class XmlAttribute;
}

namespace storage::distributor {

class DistributorStripeInterface;
class BucketSpaceDistributionContext;

class StripeBucketDBUpdater final
    : public framework::StatusReporter,
      public api::MessageHandler
{
public:
    StripeBucketDBUpdater(const DistributorNodeContext& node_ctx,
                          DistributorStripeOperationContext& op_ctx,
                          DistributorStripeInterface& owner,
                          DistributorMessageSender& sender);
    ~StripeBucketDBUpdater() override;

    void flush();
    const lib::ClusterState* pendingClusterStateOrNull(const document::BucketSpace&) const;
    void recheckBucketInfo(uint32_t nodeIdx, const document::Bucket& bucket);
    void handle_activated_cluster_state_bundle();

    bool onRequestBucketInfoReply(const std::shared_ptr<api::RequestBucketInfoReply> & repl) override;
    bool onMergeBucketReply(const std::shared_ptr<api::MergeBucketReply>& reply) override;
    bool onNotifyBucketChange(const std::shared_ptr<api::NotifyBucketChangeCommand>&) override;
    void resendDelayedMessages();

    vespalib::string reportXmlStatus(vespalib::xml::XmlOutputStream&, const framework::HttpUrlPath&) const;
    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    // Functions used for state reporting when a StripeAccessGuard is held.
    void report_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const;
    void report_delayed_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const;

    void print(std::ostream& out, bool verbose, const std::string& indent) const;
    const DistributorNodeContext& node_context() const { return _node_ctx; }
    DistributorStripeOperationContext& operation_context() { return _op_ctx; }

    void set_stale_reads_enabled(bool enabled) noexcept {
        _stale_reads_enabled.store(enabled, std::memory_order_relaxed);
    }
    bool stale_reads_enabled() const noexcept {
        return _stale_reads_enabled.load(std::memory_order_relaxed);
    }

    OperationRoutingSnapshot read_snapshot_for_bucket(const document::Bucket&) const;

    void reset_all_last_gc_timestamps_to_current_time();
private:
    class MergeReplyGuard {
    public:
        MergeReplyGuard(DistributorStripeInterface& distributor_interface, const std::shared_ptr<api::MergeBucketReply>& reply) noexcept
            : _distributor_interface(distributor_interface), _reply(reply) {}

        ~MergeReplyGuard();

        // Used when we're flushing and simply want to drop the reply rather
        // than send it down
        void resetReply() { _reply.reset(); }
    private:
        DistributorStripeInterface& _distributor_interface;
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

    friend class DistributorStripeTestUtil;
    // TODO refactor and rewire to avoid needing this direct meddling
    friend class DistributorStripe;

    // Only to be used by tests that want to ensure both the BucketDBUpdater _and_ the Distributor
    // components agree on the currently active cluster state bundle.
    // Transitively invokes Distributor::enableClusterStateBundle
    void simulate_cluster_state_bundle_activation(const lib::ClusterStateBundle& activated_state);

    bool shouldDeferStateEnabling() const noexcept;
    bool hasPendingClusterState() const;
    bool processSingleBucketInfoReply(const std::shared_ptr<api::RequestBucketInfoReply>& repl);
    void handleSingleBucketInfoFailure(const std::shared_ptr<api::RequestBucketInfoReply>& repl,
                                       const BucketRequest& req);
    void mergeBucketInfoWithDatabase(const std::shared_ptr<api::RequestBucketInfoReply>& repl,
                                     const BucketRequest& req);
    static void convertBucketInfoToBucketList(const std::shared_ptr<api::RequestBucketInfoReply>& repl,
                                              uint16_t targetNode, BucketListMerger::BucketList& newList);
    void sendRequestBucketInfo(uint16_t node, const document::Bucket& bucket,
                               const std::shared_ptr<MergeReplyGuard>& mergeReplystatic );
    static void addBucketInfoForNode(const BucketDatabase::Entry& e, uint16_t node,
                                     BucketListMerger::BucketList& existing);
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

    void update_read_snapshot_before_db_pruning();
    void update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state);
    void update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state);

    PotentialDataLossReport remove_superfluous_buckets(document::BucketSpace bucket_space,
                                                       const lib::ClusterState& new_state,
                                                       bool is_distribution_change);
    void merge_entries_into_db(document::BucketSpace bucket_space,
                               api::Timestamp gathered_at_timestamp,
                               const lib::Distribution& distribution,
                               const lib::ClusterState& new_state,
                               const char* storage_up_states,
                               const std::unordered_set<uint16_t>& outdated_nodes,
                               const std::vector<dbtransition::Entry>& entries);

    void enqueueRecheckUntilPendingStateEnabled(uint16_t node, const document::Bucket&);
    void sendAllQueuedBucketRechecks();

    /**
       Removes all copies of buckets that are on nodes that are down.
    */
    class MergingNodeRemover : public BucketDatabase::MergingProcessor {
    public:
        MergingNodeRemover(const lib::ClusterState& s,
                           uint16_t localIndex,
                           const lib::Distribution& distribution,
                           const char* upStates,
                           bool track_non_owned_entries);
        ~MergingNodeRemover() override;

        Result merge(BucketDatabase::Merger&) override;
        static void logRemove(const document::BucketId& bucketId, const char* msg) ;
        bool distributorOwnsBucket(const document::BucketId&) const;

        const std::vector<BucketDatabase::Entry>& getNonOwnedEntries() const noexcept {
            return _nonOwnedBuckets;
        }
        size_t removed_buckets() const noexcept { return _removed_buckets; }
        size_t removed_documents() const noexcept { return _removed_documents; }
    private:
        void setCopiesInEntry(BucketDatabase::Entry& e, const std::vector<BucketCopy>& copies) const;

        bool has_unavailable_nodes(const BucketDatabase::Entry&) const;
        bool storage_node_is_available(uint16_t index) const noexcept;

        const lib::ClusterState            _state;
        std::vector<bool>                  _available_nodes;
        std::vector<BucketDatabase::Entry> _nonOwnedBuckets;
        size_t                             _removed_buckets;
        size_t                             _removed_documents;
        uint16_t                           _localIndex;
        const lib::Distribution&           _distribution;
        const char*                        _upStates;
        bool                               _track_non_owned_entries;
        mutable uint64_t                   _cachedDecisionSuperbucket;
        mutable bool                       _cachedOwned;
    };

    using DistributionContexts = std::unordered_map<document::BucketSpace,
                                                    std::shared_ptr<BucketSpaceDistributionContext>,
                                                    document::BucketSpace::hash>;
    using DbGuards = std::unordered_map<document::BucketSpace,
                                        std::shared_ptr<BucketDatabase::ReadGuard>,
                                        document::BucketSpace::hash>;
    using DelayedRequestsQueue = std::deque<std::pair<vespalib::steady_time, BucketRequest>>;

    const DistributorNodeContext&      _node_ctx;
    DistributorStripeOperationContext& _op_ctx;
    DistributorStripeInterface&        _distributor_interface;
    DelayedRequestsQueue               _delayedRequests;
    std::map<uint64_t, BucketRequest>  _sentMessages;
    DistributorMessageSender&          _sender;
    std::set<EnqueuedBucketRecheck>    _enqueuedRechecks;
    std::atomic<bool>                  _stale_reads_enabled;
    DistributionContexts               _active_distribution_contexts;
    DbGuards                           _explicit_transition_read_guard;
    mutable std::mutex                 _distribution_context_mutex;
};

}
