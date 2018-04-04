// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/distributor/operations/sequenced_operation.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>

namespace document {
    class Document;
}
namespace storage::lib {
    class Distribution;
}
namespace storage::api {
    class CreateBucketReply;
    class PutCommand;
}
namespace storage::distributor {

class DistributorBucketSpace;
class OperationTargetList;

class PutOperation : public SequencedOperation
{
public:
    PutOperation(DistributorComponent& manager, DistributorBucketSpace &bucketSpace,
                 std::shared_ptr<api::PutCommand> msg, PersistenceOperationMetricSet& metric,
                 SequencingHandle sequencingHandle = SequencingHandle());

    void onStart(DistributorMessageSender& sender) override;
    const char* getName() const override { return "put"; };
    std::string getStatus() const override { return ""; };
    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    void onClose(DistributorMessageSender& sender) override;

    static void getTargetNodes(const std::vector<uint16_t>& idealNodes, std::vector<uint16_t>& targetNodes,
                               std::vector<uint16_t>& createNodes, const BucketInfo& bucketInfo, uint32_t redundancy);
private:
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;

    void insertDatabaseEntryAndScheduleCreateBucket(const OperationTargetList& copies, bool setOneActive,
                                                    const api::StorageCommand& originalCommand,
                                                    std::vector<MessageTracker::ToSend>& messagesToSend);

    void sendPutToBucketOnNode(document::BucketSpace bucketSpace, const document::BucketId& bucketId,
                               const uint16_t node, std::vector<PersistenceMessageTracker::ToSend>& putBatch);

    bool shouldImplicitlyActivateReplica(const OperationTargetList& targets) const;

    std::shared_ptr<api::PutCommand> _msg;
    DistributorComponent& _manager;
    DistributorBucketSpace &_bucketSpace;
};

}
