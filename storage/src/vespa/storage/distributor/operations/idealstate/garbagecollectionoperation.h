// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idealstateoperation.h"
#include <vespa/storage/distributor/messagetracker.h>

namespace storage::distributor {

class PendingMessageTracker;

class GarbageCollectionOperation : public IdealStateOperation
{
public:
    GarbageCollectionOperation(const std::string& clusterName, const BucketAndNodes& nodes);
    ~GarbageCollectionOperation();

    void onStart(DistributorMessageSender& sender) override;
    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    const char* getName() const override { return "garbagecollection"; };
    Type getType() const override { return GARBAGE_COLLECTION; }
    bool shouldBlockThisOperation(uint32_t, uint8_t) const override;

protected:
    MessageTracker _tracker;
};

}
