// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idealstateoperation.h"
#include <vespa/storage/bucketdb/bucketcopy.h>
#include <vespa/storage/distributor/messagetracker.h>
#include <vector>

namespace storage::distributor {

class PendingMessageTracker;

class GarbageCollectionOperation : public IdealStateOperation
{
public:
    GarbageCollectionOperation(const ClusterContext& cluster_ctx,
                               const BucketAndNodes& nodes);
    ~GarbageCollectionOperation() override;

    void onStart(DistributorMessageSender& sender) override;
    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    const char* getName() const override { return "garbagecollection"; };
    Type getType() const override { return GARBAGE_COLLECTION; }
    bool shouldBlockThisOperation(uint32_t, uint8_t) const override;

protected:
    MessageTracker _tracker;
private:
    std::vector<BucketCopy> _replica_info;
    uint32_t _max_documents_removed;

    void merge_received_bucket_info_into_db();
    void update_gc_metrics();
};

}
