// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/distributor/operations/idealstate/idealstateoperation.h>
#include <vespa/storage/distributor/operations/idealstate/mergelimiter.h>
#include <vespa/storage/distributor/operations/idealstate/mergemetadata.h>
#include <vespa/storage/distributor/operations/idealstate/removebucketoperation.h>
#include <vespa/storageapi/message/bucket.h>

namespace storage {
namespace lib {
    class Distribution;
}
namespace distributor {

class MergeOperation : public IdealStateOperation
{
protected:
    bool sourceOnlyCopyChangedDuringMerge(const BucketDatabase::Entry&) const;

    framework::SecondTime _sentMessageTime;
    std::vector<api::MergeBucketCommand::Node> _mnodes;
    std::unique_ptr<RemoveBucketOperation> _removeOperation;
    BucketInfo _infoBefore;
    MergeLimiter _limiter;

public:
    static const int LOAD = 10;

    MergeOperation(const BucketAndNodes& nodes,
                   uint16_t maxNodes = 16)
        : IdealStateOperation(nodes),
          _sentMessageTime(0),
          _limiter(maxNodes)
    {}

    ~MergeOperation();

    void onStart(DistributorMessageSender& sender);

    void onReceive(DistributorMessageSender& sender,
                   const api::StorageReply::SP&);

    const char* getName() const { return "merge"; };

    std::string getStatus() const;

    Type getType() const { return MERGE_BUCKET; }

    /** Generates ordered list of nodes that should be included in the merge */
    static void generateSortedNodeList(
            const lib::Distribution&, const lib::ClusterState&,
            const document::BucketId&, MergeLimiter&,
            std::vector<MergeMetaData>&);

    bool shouldBlockThisOperation(uint32_t messageType, uint8_t pri) const;
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
                               DistributorMessageSender& sender);
};

} // distributor
} // storage
