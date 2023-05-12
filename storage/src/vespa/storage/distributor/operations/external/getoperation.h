// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "newest_replica.h"
#include <vespa/storageapi/defs.h>
#include <vespa/storage/distributor/operations/operation.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <optional>

namespace document { class Document; }

namespace storage::api { class GetCommand; }

namespace storage::distributor {

class DistributorNodeContext;
class DistributorBucketSpace;
class PersistenceOperationMetricSet;

class GetOperation  : public Operation
{
public:
    GetOperation(const DistributorNodeContext& node_ctx,
                 const DistributorBucketSpace& bucketSpace,
                 const std::shared_ptr<BucketDatabase::ReadGuard>& read_guard,
                 std::shared_ptr<api::GetCommand> msg,
                 PersistenceOperationMetricSet& metric,
                 api::InternalReadConsistency desired_read_consistency = api::InternalReadConsistency::Strong);

    void onClose(DistributorStripeMessageSender& sender) override;
    void onStart(DistributorStripeMessageSender& sender) override;
    void onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg) override;
    const char* getName() const noexcept override { return "get"; }
    std::string getStatus() const override { return ""; }

    [[nodiscard]] bool all_bucket_metadata_initially_consistent() const noexcept;
    [[nodiscard]] bool any_replicas_failed() const noexcept {
        return _any_replicas_failed;
    }

    // Exposed for unit testing. TODO feels a bit dirty :I
    const DistributorBucketSpace& bucketSpace() const noexcept { return _bucketSpace; }

    const std::vector<std::pair<document::BucketId, uint16_t>>& replicas_in_db() const noexcept {
        return _replicas_in_db;
    }

    api::InternalReadConsistency desired_read_consistency() const noexcept {
        return _desired_read_consistency;
    }

    // Note: in the case the document could not be found on any replicas, but
    // at least one node returned a non-error response, the returned value will
    // have a timestamp of zero and the most recently asked node as its node.
    const std::optional<NewestReplica>& newest_replica() const noexcept {
        return _newest_replica;
    }

private:
    class GroupId {
    public:
        // Node should be set only if bucket is incomplete
        GroupId(const document::BucketId& id, uint32_t checksum, int node) noexcept;

        bool operator<(const GroupId& other) const noexcept;
        bool operator==(const GroupId& other) const noexcept;
        const document::BucketId& getBucketId() const noexcept { return _id; }
        int getNode() const noexcept { return _node; }
    private:
        document::BucketId _id;
        uint32_t _checksum;
        int _node;
    };

    struct BucketChecksumGroup {
        explicit BucketChecksumGroup(const BucketCopy& c) noexcept
            : copy(c), sent(0), returnCode(api::ReturnCode::OK), to_node(UINT16_MAX), received(false)
        {}

        BucketCopy copy;
        api::StorageMessage::Id sent;
        api::ReturnCode returnCode;
        uint16_t to_node;
        bool received;
    };

    using GroupVector = std::vector<BucketChecksumGroup>;
    using DbReplicaState = std::vector<std::pair<document::BucketId, uint16_t>>;

    // Organize the different copies by bucket/checksum pairs. We should
    // try to request GETs from each bucket and each different checksum
    // within that bucket.
    std::map<GroupId, GroupVector>      _responses;
    const DistributorNodeContext&       _node_ctx;
    const DistributorBucketSpace&       _bucketSpace;
    std::shared_ptr<api::GetCommand>    _msg;
    api::ReturnCode                     _returnCode;
    std::shared_ptr<document::Document> _doc;
    std::optional<NewestReplica>        _newest_replica;
    PersistenceOperationMetricSet&      _metric;
    framework::MilliSecTimer            _operationTimer;
    DbReplicaState                      _replicas_in_db;
    vespalib::Trace                     _trace;
    api::InternalReadConsistency        _desired_read_consistency;
    bool                                _has_replica_inconsistency;
    bool                                _any_replicas_failed;

    void sendReply(DistributorStripeMessageSender& sender);
    bool sendForChecksum(DistributorStripeMessageSender& sender, const document::BucketId& id, GroupVector& res);

    void assignTargetNodeGroups(const BucketDatabase::ReadGuard& read_guard);
    bool copyIsOnLocalNode(const BucketCopy&) const;
    /**
     * Returns the vector index of the target to send to, or -1 if none
     * could be found (i.e. all targets have already been sent to).
     */
    int findBestUnsentTarget(const GroupVector& candidates) const;

    void update_internal_metrics();
};

}
