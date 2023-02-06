// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idealstateoperation.h"
#include "mergelimiter.h"
#include "mergemetadata.h"
#include "removebucketoperation.h"
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storageapi/message/bucket.h>

namespace storage::lib { class Distribution; }

namespace storage::distributor {

class MergeBucketMetricSet;

class MergeOperation : public IdealStateOperation
{
protected:
    bool sourceOnlyCopyChangedDuringMerge(const BucketDatabase::Entry&) const;

    vespalib::steady_time _sentMessageTime;
    std::vector<api::MergeBucketCommand::Node> _mnodes;
    std::unique_ptr<RemoveBucketOperation> _removeOperation;
    BucketInfo _infoBefore;
    MergeLimiter _limiter;

public:

    MergeOperation(const BucketAndNodes& nodes, uint16_t maxNodes = 16)
        : IdealStateOperation(nodes),
          _sentMessageTime(),
          _limiter(maxNodes)
    {}

    ~MergeOperation() override;

    void onStart(DistributorStripeMessageSender& sender) override;
    void onReceive(DistributorStripeMessageSender& sender, const api::StorageReply::SP&) override;
    const char* getName() const noexcept override { return "merge"; };
    std::string getStatus() const override;
    Type getType() const noexcept override { return MERGE_BUCKET; }

    /** Generates ordered list of nodes that should be included in the merge */
    static void generateSortedNodeList(
            const lib::Distribution&, const lib::ClusterState&,
            const document::BucketId&, MergeLimiter&,
            std::vector<MergeMetaData>&);

    bool shouldBlockThisOperation(uint32_t messageType, uint16_t node, uint8_t pri) const override;
    bool isBlocked(const DistributorStripeOperationContext& ctx, const OperationSequencer&) const override;
private:
    static void addIdealNodes(
            const std::vector<uint16_t>& idealNodes,
            const std::vector<MergeMetaData>& nodes,
            std::vector<MergeMetaData>& result);

    static void addCopiesNotAlreadyAdded(
            uint16_t redundancy,
            const std::vector<MergeMetaData>& nodes,
            std::vector<MergeMetaData>& result);

    void deleteSourceOnlyNodes(const BucketDatabase::Entry& currentState,
                               DistributorStripeMessageSender& sender);
    bool is_global_bucket_merge() const noexcept;
    bool all_involved_nodes_support_unordered_merge_chaining() const noexcept;
    MergeBucketMetricSet* get_merge_metrics();
};

}
