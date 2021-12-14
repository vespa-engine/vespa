// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

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
    UpdateOperation(const DistributorNodeContext& node_ctx,
                    DistributorStripeOperationContext& op_ctx,
                    DistributorBucketSpace& bucketSpace,
                    const std::shared_ptr<api::UpdateCommand>& msg,
                    std::vector<BucketDatabase::Entry> entries,
                    UpdateMetricSet& metric);

    void onStart(DistributorStripeMessageSender& sender) override;
    const char* getName() const override { return "update"; };
    std::string getStatus() const override { return ""; };
    void onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg) override;
    void onClose(DistributorStripeMessageSender& sender) override;

    std::pair<document::BucketId, uint16_t> getNewestTimestampLocation() const {
        return _newestTimestampLocation;
    }

private:
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;
    std::shared_ptr<api::UpdateCommand> _msg;
    std::vector<BucketDatabase::Entry> _entries;
    const api::Timestamp _new_timestamp;
    const bool _is_auto_create_update;

    const DistributorNodeContext& _node_ctx;
    DistributorStripeOperationContext& _op_ctx;
    DistributorBucketSpace &_bucketSpace;
    std::pair<document::BucketId, uint16_t> _newestTimestampLocation;
    api::BucketInfo _infoAtSendTime; // Should be same across all replicas

    bool anyStorageNodesAvailable() const;

    class PreviousDocumentVersion {
    public:
        PreviousDocumentVersion(document::BucketId b, const api::BucketInfo& info, uint64_t o, uint16_t node) noexcept :
            bucketId(b), bucketInfo(info), oldTs(o), nodeId(node) {}

        document::BucketId bucketId;
        api::BucketInfo bucketInfo;
        uint64_t oldTs;
        uint16_t nodeId;
    };

    std::vector<PreviousDocumentVersion> _results;
    UpdateMetricSet& _metrics;

    api::Timestamp adjusted_received_old_timestamp(api::Timestamp old_ts_from_node) const;
    void log_inconsistency_warning(const api::UpdateReply& reply,
                                   const PreviousDocumentVersion& highest_timestamped_version,
                                   const PreviousDocumentVersion& low_timestamped_version);
};

}

}
