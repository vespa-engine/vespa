// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idealstateoperation.h"
#include <vespa/storage/distributor/messagetracker.h>

namespace storage::distributor {

class SetBucketStateOperation : public IdealStateOperation
{
public:
    SetBucketStateOperation(const ClusterContext& cluster_ctx,
                            const BucketAndNodes& nodes,
                            const std::vector<uint16_t>& wantedActiveNodes);
    ~SetBucketStateOperation() override;

    void onStart(DistributorMessageSender&) override;
    void onReceive(DistributorMessageSender&, const std::shared_ptr<api::StorageReply>&) override;
    const char* getName() const override { return "setbucketstate"; }
    Type getType() const override { return SET_BUCKET_STATE; }
protected:
    MessageTracker _tracker;
    std::vector<uint16_t> _wantedActiveNodes;

private:
    void enqueueSetBucketStateCommand(uint16_t node, bool active);
    void activateNode(DistributorMessageSender& sender);
    void deactivateNodes(DistributorMessageSender& sender);
    bool shouldBeActive(uint16_t node) const;
};

}
