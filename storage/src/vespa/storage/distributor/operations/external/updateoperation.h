// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>

namespace document {
class Document;
}

namespace storage {

namespace api {
class UpdateCommand;
class CreateBucketReply;
}

namespace distributor {

class UpdateOperation : public Operation
{
public:
    UpdateOperation(DistributorComponent& manager,
                    const std::shared_ptr<api::UpdateCommand> & msg,
                    PersistenceOperationMetricSet& metric);

    void onStart(DistributorMessageSender& sender) override;
    const char* getName() const override { return "update"; };
    std::string getStatus() const override { return ""; };
    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg) override;
    void onClose(DistributorMessageSender& sender) override;

    std::pair<document::BucketId, uint16_t> getNewestTimestampLocation() const {
        return _newestTimestampLocation;
    }

private:
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;
    std::shared_ptr<api::UpdateCommand> _msg;

    DistributorComponent& _manager;
    std::pair<document::BucketId, uint16_t> _newestTimestampLocation;

    bool anyStorageNodesAvailable() const;

    class OldTimestamp {
    public:
        OldTimestamp(document::BucketId b, uint64_t o, uint16_t node) :
            bucketId(b), oldTs(o), nodeId(node) {}

        document::BucketId bucketId;
        uint64_t oldTs;
        uint16_t nodeId;
    };

    std::vector<OldTimestamp> _results;
};

}

}
