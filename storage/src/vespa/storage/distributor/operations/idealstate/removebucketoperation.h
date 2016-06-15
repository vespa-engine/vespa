// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operations/idealstate/idealstateoperation.h>
#include <vespa/storage/distributor/messagetracker.h>

namespace storage
{

namespace distributor
{

class PendingMessageTracker;

class RemoveBucketOperation : public IdealStateOperation
{
public:
    RemoveBucketOperation(
            const std::string& clusterName,
            const BucketAndNodes& nodes)
        : IdealStateOperation(nodes), _tracker(clusterName) {};

    /**
       Sends messages, returns true if we are done (sent nothing).
    */
    bool onStartInternal(DistributorMessageSender& sender);

    /**
       Sends messages, calls done() if we are done (sent nothing).
    */
    void onStart(DistributorMessageSender& sender);

    bool onReceiveInternal(const std::shared_ptr<api::StorageReply> &);

    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> &);

    const char* getName() const { return "remove"; };

    Type getType() const { return DELETE_BUCKET; }

    bool shouldBlockThisOperation(uint32_t, uint8_t) const;

protected:
    MessageTracker _tracker;
};

}

}

