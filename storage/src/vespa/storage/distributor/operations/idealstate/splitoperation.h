// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idealstateoperation.h"
#include <vespa/storage/distributor/messagetracker.h>

namespace storage::distributor {

class SplitOperation : public IdealStateOperation
{
public:
    SplitOperation(const ClusterContext& cluster_ctx,
                   const BucketAndNodes& nodes,
                   uint32_t maxBits, uint32_t splitCount, uint32_t splitSize);
    SplitOperation(const SplitOperation &) = delete;
    SplitOperation & operator = (const SplitOperation &) = delete;
    ~SplitOperation();

    void onStart(DistributorMessageSender& sender) override;
    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    const char* getName() const override { return "split"; };
    Type getType() const override { return SPLIT_BUCKET; }
    bool isBlocked(const PendingMessageTracker&, const OperationSequencer&) const override;
    bool shouldBlockThisOperation(uint32_t, uint8_t) const override;
protected:
    MessageTracker _tracker;

    uint32_t _maxBits;
    uint32_t _splitCount;
    uint32_t _splitSize;
};

}
