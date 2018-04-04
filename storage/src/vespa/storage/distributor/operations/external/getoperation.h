// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/defs.h>
#include <vespa/storage/distributor/operations/operation.h>
#include <vespa/storage/bucketdb/bucketcopy.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageframework/generic/clock/timer.h>

namespace document { class Document; }

namespace storage {

namespace api { class GetCommand; }

class PersistenceOperationMetricSet;

namespace distributor {

class DistributorComponent;
class DistributorBucketSpace;

class GetOperation  : public Operation
{
public:
    GetOperation(DistributorComponent& manager, DistributorBucketSpace &bucketSpace,
                 std::shared_ptr<api::GetCommand> msg, PersistenceOperationMetricSet& metric);

    void onClose(DistributorMessageSender& sender) override;
    void onStart(DistributorMessageSender& sender) override;
    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg) override;
    const char* getName() const override { return "get"; }
    std::string getStatus() const override { return ""; }

    bool hasConsistentCopies() const;

private:
    class GroupId {
    public:
        // Node should be set only if bucket is incomplete
        GroupId(const document::BucketId& id, uint32_t checksum, int node);

        bool operator<(const GroupId& other) const;
        bool operator==(const GroupId& other) const;
        const document::BucketId& getBucketId() const { return _id; }
        int getNode() const { return _node; }
    private:
        document::BucketId _id;
        uint32_t _checksum;
        int _node;
    };

    class BucketChecksumGroup {
    public:
        BucketChecksumGroup(const BucketCopy& c) :
            copy(c),
            sent(0), received(false), returnCode(api::ReturnCode::OK)
        {}

        BucketCopy copy;
        api::StorageMessage::Id sent;
        bool received;
        api::ReturnCode returnCode;
    };

    typedef std::vector<BucketChecksumGroup> GroupVector;

    // Organize the different copies by bucket/checksum pairs. We should
    // try to request GETs from each bucket and each different checksum
    // within that bucket.
    std::map<GroupId, GroupVector> _responses;

    DistributorComponent& _manager;
    DistributorBucketSpace &_bucketSpace;

    std::shared_ptr<api::GetCommand> _msg;

    api::ReturnCode _returnCode;
    std::shared_ptr<document::Document> _doc;

    api::Timestamp _lastModified;

    PersistenceOperationMetricSet& _metric;
    framework::MilliSecTimer _operationTimer;

    void sendReply(DistributorMessageSender& sender);
    bool sendForChecksum(DistributorMessageSender& sender, const document::BucketId& id, GroupVector& res);

    void assignTargetNodeGroups();
    bool copyIsOnLocalNode(const BucketCopy&) const;
    /**
     * Returns the vector index of the target to send to, or -1 if none
     * could be found (i.e. all targets have already been sent to).
     */
    int findBestUnsentTarget(const GroupVector& candidates) const;
};

}
}
