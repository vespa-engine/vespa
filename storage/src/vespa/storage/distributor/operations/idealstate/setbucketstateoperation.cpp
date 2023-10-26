// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "setbucketstateoperation.h"
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storageapi/message/bucket.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operation.idealstate.setactive");

namespace storage::distributor {

SetBucketStateOperation::SetBucketStateOperation(const ClusterContext& cluster_ctx,
                                                 const BucketAndNodes& nodes,
                                                 const std::vector<uint16_t>& wantedActiveNodes)
    : IdealStateOperation(nodes),
      _tracker(cluster_ctx),
      _wantedActiveNodes(wantedActiveNodes)
{ }

SetBucketStateOperation::~SetBucketStateOperation() = default;

void
SetBucketStateOperation::enqueueSetBucketStateCommand(uint16_t node, bool active) {
    auto msg = std::make_shared<api::SetBucketStateCommand>(getBucket(), api::SetBucketStateCommand::toState(active));
    LOG(debug, "Enqueuing %s for %s to node %u", active ? "Activate" : "Deactivate", getBucketId().toString().c_str(), node);
    setCommandMeta(*msg);
    _tracker.queueCommand(std::move(msg), node);
}

bool
SetBucketStateOperation::shouldBeActive(uint16_t node) const
{
    for (uint16_t wantedActiveNode : _wantedActiveNodes) {
        if (wantedActiveNode == node) {
            return true;
        }
    }
    return false;
}

void
SetBucketStateOperation::activateNode(DistributorStripeMessageSender& sender) {
    for (uint16_t wantedActiveNode : _wantedActiveNodes) {
        enqueueSetBucketStateCommand(wantedActiveNode, true);
    }
    _tracker.flushQueue(sender);
    _ok = true;
}


void
SetBucketStateOperation::deactivateNodes(DistributorStripeMessageSender& sender) {
    for (uint16_t node : getNodes()) {
        if (!shouldBeActive(node)) {
            enqueueSetBucketStateCommand(node, false);
        }
    }
    _tracker.flushQueue(sender);
}

void
SetBucketStateOperation::onStart(DistributorStripeMessageSender& sender)
{
    activateNode(sender);
}

void
SetBucketStateOperation::onReceive(DistributorStripeMessageSender& sender,
                                   const std::shared_ptr<api::StorageReply>& reply)
{
    auto& rep = dynamic_cast<api::SetBucketStateReply&>(*reply);

    const uint16_t node = _tracker.handleReply(rep);
    LOG(debug, "Got %s from node %u", reply->toString(true).c_str(), node);

    bool deactivate = false;

    if (_cancel_scope.node_is_cancelled(node)) {
        LOG(debug, "SetBucketState for %s has been cancelled", rep.getBucketId().toString().c_str());
        _ok = false;
    } else if (reply->getResult().success()) {
        BucketDatabase::Entry entry = _bucketSpace->getBucketDatabase().get(rep.getBucketId());

        if (entry.valid()) {
            const BucketCopy* copy = entry->getNode(node);
            if (copy) {
                api::BucketInfo bInfo = copy->getBucketInfo();

                if (shouldBeActive(node)) {
                    bInfo.setActive(true);
                    deactivate = true;
                } else {
                    bInfo.setActive(false);
                }

                entry->updateNode(BucketCopy(_manager->operation_context().generate_unique_timestamp(), node, bInfo)
                                            .setTrusted(copy->trusted()));

                _bucketSpace->getBucketDatabase().update(entry);
            }
        } else {
            LOG(debug, "%s did not exist when receiving %s",
                rep.getBucketId().toString().c_str(), rep.toString(true).c_str());
        }
    } else {
        LOG(debug, "Failed setting state for %s on node %u: %s",
            rep.getBucketId().toString().c_str(), node, reply->getResult().toString().c_str());
        _ok = false;
    }
    if (deactivate) {
        deactivateNodes(sender);
    }

    if (_tracker.finished()) {
        done();
    }
}

}
