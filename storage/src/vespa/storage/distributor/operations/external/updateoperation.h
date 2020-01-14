// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

class UpdateMetricSet;

namespace distributor {

class DistributorBucketSpace;

class UpdateOperation : public Operation
{
public:
    UpdateOperation(DistributorComponent& manager,
                    DistributorBucketSpace &bucketSpace,
                    const std::shared_ptr<api::UpdateCommand> & msg,
                    UpdateMetricSet& metric);

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
    const api::Timestamp _new_timestamp;
    const bool _is_auto_create_update;

    DistributorComponent& _manager;
    DistributorBucketSpace &_bucketSpace;
    std::pair<document::BucketId, uint16_t> _newestTimestampLocation;
    api::BucketInfo _infoAtSendTime; // Should be same across all replicas

    bool anyStorageNodesAvailable() const;

    class PreviousDocumentVersion {
    public:
        PreviousDocumentVersion(document::BucketId b, const api::BucketInfo& info, uint64_t o, uint16_t node) :
            bucketId(b), bucketInfo(info), oldTs(o), nodeId(node) {}

        document::BucketId bucketId;
        api::BucketInfo bucketInfo;
        uint64_t oldTs;
        uint16_t nodeId;
    };

    std::vector<PreviousDocumentVersion> _results;
    UpdateMetricSet& _metrics;

    api::Timestamp adjusted_received_old_timestamp(api::Timestamp old_ts_from_node) const;
};

}

}
