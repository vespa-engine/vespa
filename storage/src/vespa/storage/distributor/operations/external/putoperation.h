// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/distributor/operations/sequenced_operation.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>
#include <vespa/storage/distributor/operationtargetresolver.h>

namespace document {
    class Document;
}
namespace storage {
namespace lib {
    class Distribution;
}
namespace api {
    class CreateBucketReply;
    class PutCommand;
}
namespace distributor {

class PutOperation : public SequencedOperation
{
public:
    PutOperation(DistributorComponent& manager,
                 const std::shared_ptr<api::PutCommand> & msg,
                 PersistenceOperationMetricSet& metric,
                 SequencingHandle sequencingHandle = SequencingHandle());

    void onStart(DistributorMessageSender& sender) override;

    const char* getName() const override { return "put"; }

    std::string getStatus() const override { return ""; }

    void onReceive(DistributorMessageSender& sender,
                   const std::shared_ptr<api::StorageReply> &) override;

    void onClose(DistributorMessageSender& sender) override;

    /**
     * Gets the ideal state of the given bucket, and adds all nodes from the
     * ideal state to targetNodes. Also schedules create bucket messages for
     * all buckets currently not in the nodes list, and sets nodes in the node
     * list not in the ideal state to untrusted.
     */
    static bool checkCreateBucket(const lib::Distribution& distribution,
                                  const lib::ClusterState& state,
                                  BucketDatabase::Entry& e,
                                  std::vector<uint16_t>& targetNodes,
                                  std::vector<MessageTracker::ToSend>& messagesToSend,
                                  const api::StorageCommand& originalCommand);

    static void getTargetNodes(const std::vector<uint16_t>& idealNodes,
                               std::vector<uint16_t>& targetNodes,
                               std::vector<uint16_t>& createNodes,
                               const BucketInfo& bucketInfo,
                               uint32_t redundancy);
private:
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;

    void insertDatabaseEntryAndScheduleCreateBucket(
            const OperationTargetList& copies,
            bool setOneActive,
            const api::StorageCommand& originalCommand,
            std::vector<MessageTracker::ToSend>& messagesToSend);

    void sendPutToBucketOnNode(
            const document::BucketId& bucketId,
            const uint16_t node,
            std::vector<PersistenceMessageTracker::ToSend>& putBatch);

    bool shouldImplicitlyActivateReplica(
            const OperationTargetList& targets) const;

    std::shared_ptr<api::PutCommand> _msg;
    DistributorComponent& _manager;
};

} // distributor
} // storage
