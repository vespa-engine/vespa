// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operations/idealstate/idealstateoperation.h>
#include <vespa/storage/distributor/messagetracker.h>

namespace storage
{

namespace distributor
{

class SetBucketStateOperation : public IdealStateOperation
{
public:
    SetBucketStateOperation(const std::string& clusterName,
                            const BucketAndNodes& nodes,
                            const std::vector<uint16_t>& wantedActiveNodes)
        : IdealStateOperation(nodes),
          _tracker(clusterName),
          _wantedActiveNodes(wantedActiveNodes)
    {
    }

    void onStart(DistributorMessageSender&);

    void onReceive(DistributorMessageSender&, const std::shared_ptr<api::StorageReply>&);

    const char* getName() const { return "setbucketstate"; }

    virtual Type getType() const { return SET_BUCKET_STATE; }

protected:
    MessageTracker _tracker;
    std::vector<uint16_t> _wantedActiveNodes;

private:
    void enqueueSetBucketStateCommand(uint16_t node, bool active);
    void activateNode(DistributorMessageSender& sender);
    void deactivateNodes(DistributorMessageSender& sender);
    bool shouldBeActive(uint16_t node) const;
};

} // namespace distributor

} // namespace storage

