// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operations/operation.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/visiting/memory_bounded_trace.h>
#include <vespa/storageapi/defs.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageframework/generic/clock/timer.h>

namespace document { class Document; }

namespace storage { struct VisitorMetricSet; }
namespace storage::lib { class ClusterState; }

namespace storage::distributor {

class DistributorComponent;
class DistributorBucketSpace;

class VisitorOperation  : public Operation
{
public:
    struct Config {
        Config(uint32_t minBucketsPerVisitor_,
               uint32_t maxVisitorsPerNodePerVisitor_)
            : minBucketsPerVisitor(minBucketsPerVisitor_),
              maxVisitorsPerNodePerVisitor(maxVisitorsPerNodePerVisitor_) {}

        uint32_t minBucketsPerVisitor;
        uint32_t maxVisitorsPerNodePerVisitor;
    };

    VisitorOperation(DistributorComponent& manager,
                     DistributorBucketSpace &bucketSpace,
                     const std::shared_ptr<api::CreateVisitorCommand> & msg,
                     const Config& config,
                     VisitorMetricSet& metrics);

    ~VisitorOperation();

    void onClose(DistributorMessageSender& sender) override;
    void onStart(DistributorMessageSender& sender) override;
    void onReceive(DistributorMessageSender& sender,
                   const std::shared_ptr<api::StorageReply> & msg) override;

    const char* getName() const override { return "visit"; }
    std::string getStatus() const override { return ""; }

private:
    struct BucketInfo {
        bool done;
        int activeNode;
        uint16_t failedCount;
        std::vector<uint16_t> triedNodes;

        BucketInfo() : done(false), activeNode(-1), failedCount(0), triedNodes() { }

        void print(vespalib::asciistream & out) const;
        vespalib::string toString() const;
    };

    typedef std::map<document::BucketId, BucketInfo> VisitBucketMap;

    struct SuperBucketInfo {
        document::BucketId bid;
        bool subBucketsCompletelyExpanded;
        VisitBucketMap subBuckets;
        std::vector<document::BucketId> subBucketsVisitOrder;

        SuperBucketInfo(const document::BucketId& b = document::BucketId(0))
            : bid(b),
              subBucketsCompletelyExpanded(false)
        {
        }

    };

    typedef std::map<uint16_t, std::vector<document::BucketId> > NodeToBucketsMap;
    typedef std::map<uint64_t, api::CreateVisitorCommand::SP> SentMessagesMap;

    void sendReply(const api::ReturnCode& code, DistributorMessageSender& sender);
    void updateReplyMetrics(const api::ReturnCode& result);
    void verifyDistributorsAreAvailable();
    void verifyVisitorDistributionBitCount(const document::BucketId&);
    void verifyDistributorIsNotDown(const lib::ClusterState&);
    void verifyDistributorOwnsBucket(const document::BucketId&);
    void verifyOperationContainsBuckets();
    void verifyOperationHasSuperbucketAndProgress();
    void verifyOperationSentToCorrectDistributor();
    bool verifyCreateVisitorCommand(DistributorMessageSender& sender);
    bool pickBucketsToVisit(const std::vector<BucketDatabase::Entry>& buckets);
    bool expandBucketAll();
    bool expandBucketContaining();
    bool expandBucketContained();
    void expandBucket();
    int pickTargetNode(
            const BucketDatabase::Entry& entry,
            const std::vector<uint16_t>& triedNodes);
    void attemptToParseOrderingSelector();
    bool documentSelectionMayHaveOrdering() const;
    bool maySendNewStorageVisitors() const noexcept;
    void startNewVisitors(DistributorMessageSender& sender);
    void initializeActiveNodes();
    bool shouldSkipBucket(const BucketInfo& bucketInfo) const;
    bool bucketIsValidAndConsistent(const BucketDatabase::Entry& entry) const;
    bool allowInconsistencies() const noexcept;
    bool shouldAbortDueToTimeout() const noexcept;
    bool assignBucketsToNodes(NodeToBucketsMap& nodeToBucketsMap);
    int getNumVisitorsToSendForNode(uint16_t node, uint32_t totalBucketsOnNode) const;
    vespalib::duration computeVisitorQueueTimeoutMs() const noexcept;
    bool sendStorageVisitors(const NodeToBucketsMap& nodeToBucketsMap,
                             DistributorMessageSender& sender);
    void sendStorageVisitor(uint16_t node,
                            const std::vector<document::BucketId>& buckets,
                            uint32_t pending,
                            DistributorMessageSender& sender);
    void markCompleted(const document::BucketId& bid, const api::ReturnCode& code);
    /**
     * Operation failed and we can pin the blame on a specific node. Updates
     * internal error code and augments error message with the index of the
     * failing node.
     */
    void markOperationAsFailedDueToNodeError(
            const api::ReturnCode& result,
            uint16_t fromFailingNodeIndex);
    /**
     * Operation failed but cannot blame a specific node in the failing context.
     * Only overwrites current error code if `result` has a higher numeric
     * code value, which avoids overwriting more critical errors.
     */
    void markOperationAsFailed(const api::ReturnCode& result);
    /**
     * Compute time remaining of visitor in milliseconds, relative to timeout
     * time point. In case of the current time having passed the timeout
     * point, function returns 0.
     */
    vespalib::duration timeLeft() const noexcept;

    DistributorComponent& _owner;
    DistributorBucketSpace &_bucketSpace;
    SentMessagesMap _sentMessages;

    api::CreateVisitorCommand::SP _msg;
    api::ReturnCode _storageError;
    bool _sentReply;

    SuperBucketInfo _superBucket;
    document::BucketId _lastBucket;

    api::Timestamp _fromTime;
    api::Timestamp _toTime;

    std::vector<uint32_t> _activeNodes;
    uint32_t _bucketCount;

    vdslib::VisitorStatistics _visitorStatistics;

    Config _config;
    VisitorMetricSet& _metrics;
    MemoryBoundedTrace _trace;

    static constexpr size_t TRACE_SOFT_MEMORY_LIMIT = 65536;

    bool done();
    bool hasNoPendingMessages();
    document::BucketId getLastBucketVisited();
    mbus::TraceNode trace;
    framework::MilliSecTimer _operationTimer;
};

}
