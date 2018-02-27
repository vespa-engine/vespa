// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketlistmerger.h"
#include "messageguard.h"
#include "distributorcomponent.h"
#include "distributormessagesender.h"
#include "pendingclusterstate.h"
#include "outdated_nodes_map.h"
#include <vespa/document/bucket/bucket.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/storageframework/generic/status/statusreporter.h>
#include <vespa/storageapi/messageapi/messagehandler.h>
#include <list>

namespace storage::distributor {

class Distributor;

class BucketDBUpdater : public framework::StatusReporter,
                        public api::MessageHandler
{
public:
    using OutdatedNodes = dbtransition::OutdatedNodes;
    using OutdatedNodesMap = dbtransition::OutdatedNodesMap;
    BucketDBUpdater(Distributor& owner,
                    DistributorBucketSpaceRepo &bucketSpaceRepo,
                    DistributorMessageSender& sender,
                    DistributorComponentRegister& compReg);
    ~BucketDBUpdater();

    void flush();
    BucketOwnership checkOwnershipInPendingState(const document::Bucket&) const;
    void recheckBucketInfo(uint32_t nodeIdx, const document::Bucket& bucket);

    bool onSetSystemState(const std::shared_ptr<api::SetSystemStateCommand>& cmd) override;
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

    bool hasPendingClusterState() const;
    bool pendingClusterStateAccepted(const std::shared_ptr<api::RequestBucketInfoReply>& repl);
    bool processSingleBucketInfoReply(const std::shared_ptr<api::RequestBucketInfoReply>& repl);
    void handleSingleBucketInfoFailure(const std::shared_ptr<api::RequestBucketInfoReply>& repl,
                                       const BucketRequest& req);
    bool isPendingClusterStateCompleted() const;
    void processCompletedPendingClusterState();
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

    void removeSuperfluousBuckets(const lib::ClusterStateBundle& newState);

    void replyToPreviousPendingClusterStateIfAny();

    void enableCurrentClusterStateBundleInDistributor();
    void addCurrentStateToClusterStateHistory();
    void enqueueRecheckUntilPendingStateEnabled(uint16_t node, const document::Bucket&);
    void sendAllQueuedBucketRechecks();

    friend class BucketDBUpdater_Test;
    friend class MergeOperation_Test;

    class BucketListGenerator
    {
    public:
        BucketListGenerator(uint16_t node, BucketListMerger::BucketList& entries)
            : _node(node), _entries(entries) {};

        bool process(BucketDatabase::Entry&);

    private:
        uint16_t _node;
        BucketListMerger::BucketList& _entries;
    };

    /**
       Removes all copies of buckets that are on nodes that are down.
    */
    class NodeRemover : public BucketDatabase::MutableEntryProcessor
    {
    public:
        NodeRemover(const lib::ClusterState& oldState,
                    const lib::ClusterState& s,
                    const document::BucketIdFactory& factory,
                    uint16_t localIndex,
                    const lib::Distribution& distribution,
                    const char* upStates)
            : _oldState(oldState),
              _state(s),
              _factory(factory),
              _localIndex(localIndex),
              _distribution(distribution),
              _upStates(upStates) {}

        ~NodeRemover();
        bool process(BucketDatabase::Entry& e) override;
        void logRemove(const document::BucketId& bucketId, const char* msg) const;
        bool distributorOwnsBucket(const document::BucketId&) const;

        const std::vector<document::BucketId>& getBucketsToRemove() const {
            return _removedBuckets;
        }
    private:
        void setCopiesInEntry(BucketDatabase::Entry& e, const std::vector<BucketCopy>& copies) const;
        void removeEmptyBucket(const document::BucketId& bucketId);

        const lib::ClusterState _oldState;
        const lib::ClusterState _state;
        std::vector<document::BucketId> _removedBuckets;

        const document::BucketIdFactory& _factory;
        uint16_t _localIndex;
        const lib::Distribution& _distribution;
        const char* _upStates;
    };

    std::deque<std::pair<framework::MilliSecTime, BucketRequest> > _delayedRequests;
    std::map<uint64_t, BucketRequest> _sentMessages;
    std::unique_ptr<PendingClusterState> _pendingClusterState;
    std::list<PendingClusterState::Summary> _history;
    DistributorMessageSender& _sender;
    std::set<EnqueuedBucketRecheck> _enqueuedRechecks;
    OutdatedNodesMap         _outdatedNodesMap;
    framework::MilliSecTimer _transitionTimer;
};

}
