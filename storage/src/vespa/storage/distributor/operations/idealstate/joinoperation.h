// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idealstateoperation.h"
#include <vespa/storage/distributor/messagetracker.h>

namespace storage::distributor {

class JoinOperation : public IdealStateOperation
{
public:
    /**
     * Creates a new join operation.
     *
     * @param clusterName The name of this storage cluster.
     * @param bucketAndNodes The bucket to join into, along with the nodes this operation uses.
     * @param bucketsToJoin The buckets to join together. The size of this array should always be either one or two.
     */
    JoinOperation(const  ClusterContext& cluster_ctx,
                  const BucketAndNodes& nodes,
                  const std::vector<document::BucketId>& bucketsToJoin);

    ~JoinOperation() override;

    void onStart(DistributorStripeMessageSender& sender) override;

    void onReceive(DistributorStripeMessageSender& sender,
                   const std::shared_ptr<api::StorageReply>&) override;

    const char* getName() const noexcept override {
        return "join";
    }

    Type getType() const noexcept override {
        return JOIN_BUCKET;
    }

    bool isBlocked(const DistributorStripeOperationContext& ctx,
                   const OperationSequencer& op_seq) const override;

protected:
    using NodeToBuckets = std::map<uint16_t, std::vector<document::BucketId>>;
    NodeToBuckets resolveSourceBucketsPerTargetNode() const;

    void fillMissingSourceBucketsForInconsistentJoins(
            NodeToBuckets& nodeToBuckets) const;

    /**
     * Returns true if any messages were enqueued, false otherwise.
     */
    bool enqueueJoinMessagePerTargetNode(const NodeToBuckets& nodeToBuckets);

    document::Bucket getJoinBucket(size_t idx) const;

    MessageTracker _tracker;
    std::vector<document::BucketId> _bucketsToJoin;
};

}
