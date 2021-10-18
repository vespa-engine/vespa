// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idealstateoperation.h"
#include <vespa/storage/distributor/messagetracker.h>

namespace storage::distributor {

class PendingMessageTracker;

class RemoveBucketOperation : public IdealStateOperation
{
public:
    RemoveBucketOperation(const ClusterContext &cluster_context, const BucketAndNodes& nodes)
        : IdealStateOperation(nodes), _tracker(cluster_context)
    {}

    /**
       Sends messages, returns true if we are done (sent nothing).
    */
    bool onStartInternal(DistributorStripeMessageSender& sender);

    /**
       Sends messages, calls done() if we are done (sent nothing).
    */
    void onStart(DistributorStripeMessageSender& sender) override;

    bool onReceiveInternal(const std::shared_ptr<api::StorageReply> &);

    void onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    const char* getName() const override { return "remove"; };
    Type getType() const override { return DELETE_BUCKET; }
    bool shouldBlockThisOperation(uint32_t, uint16_t, uint8_t) const  override;
protected:
    MessageTracker _tracker;
};

}
