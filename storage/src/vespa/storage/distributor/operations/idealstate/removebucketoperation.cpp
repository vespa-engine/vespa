// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removebucketoperation.h"
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>

#include <vespa/log/log.h>

LOG_SETUP(".distributor.operation.idealstate.remove");

using namespace storage::distributor;

bool
RemoveBucketOperation::onStartInternal(DistributorStripeMessageSender& sender)
{
    std::vector<std::pair<uint16_t, std::shared_ptr<api::DeleteBucketCommand> > > msgs;

    BucketDatabase::Entry entry = _bucketSpace->getBucketDatabase().get(getBucketId());

    for (uint32_t i = 0; i < getNodes().size(); ++i) {
        uint16_t node = getNodes()[i];
        const BucketCopy* copy(entry->getNode(node));
        if (!copy) {
            LOG(debug, "Node %u was removed between scheduling remove "
                "operation and starting it; not sending DeleteBucket to it",
                node);
            continue;
        }
        LOG(debug, "Sending DeleteBucket for %s to node %u",
            getBucketId().toString().c_str(),
            node);
        std::shared_ptr<api::DeleteBucketCommand> msg(
                new api::DeleteBucketCommand(getBucket()));
        setCommandMeta(*msg);
        msg->setBucketInfo(copy->getBucketInfo());
        msgs.push_back(std::make_pair(node, msg));
    }

    _ok = true;
    if (!getNodes().empty()) {
        _manager->operation_context().remove_nodes_from_bucket_database(getBucket(), getNodes());
        for (uint32_t i = 0; i < msgs.size(); ++i) {
            _tracker.queueCommand(msgs[i].second, msgs[i].first);
        }
        _tracker.flushQueue(sender);
    }

    return _tracker.finished();
}


void
RemoveBucketOperation::onStart(DistributorStripeMessageSender& sender)
{
    if (onStartInternal(sender)) {
        done();
    }
}

bool
RemoveBucketOperation::onReceiveInternal(const std::shared_ptr<api::StorageReply> &msg)
{
    auto* rep = dynamic_cast<api::DeleteBucketReply*>(msg.get());

    uint16_t node = _tracker.handleReply(*rep);

    LOG(debug, "Got DeleteBucket reply for %s from node %u",
        getBucketId().toString().c_str(),
        node);

    if (rep->getResult().failed()) {
        if (rep->getResult().getResult() == api::ReturnCode::REJECTED
            && rep->getBucketInfo().valid())
        {
            LOG(debug, "Got DeleteBucket rejection reply from storage for "
                "%s on node %u: %s. Reinserting node into bucket db with %s",
                getBucketId().toString().c_str(),
                node,
                vespalib::string(rep->getResult().getMessage()).c_str(),
                rep->getBucketInfo().toString().c_str());

            _manager->operation_context().update_bucket_database(
                    getBucket(),
                    BucketCopy(_manager->operation_context().generate_unique_timestamp(),
                               node,
                               rep->getBucketInfo()),
                    DatabaseUpdate::CREATE_IF_NONEXISTING);
        } else {
            LOG(info,
                "Remove operation on bucket %s failed. This distributor "
                "has already removed the bucket from the bucket database, "
                "so it is not possible to retry this operation. Failure code: %s",
                getBucketId().toString().c_str(),
                rep->getResult().toString().c_str());
        }

        _ok = false;
    }

    return _tracker.finished();
}


void
RemoveBucketOperation::onReceive(DistributorStripeMessageSender&, const std::shared_ptr<api::StorageReply> &msg)
{
    if (onReceiveInternal(msg)) {
        done();
    }
}

bool
RemoveBucketOperation::shouldBlockThisOperation(uint32_t, uint16_t target_node, uint8_t) const
{
    // Number of nodes is expected to be 1 in the vastly common case (and a highly bounded
    // number in the worst case), so a simple linear scan suffices.
    for (uint16_t node : getNodes()) {
        if (target_node == node) {
            return true;
        }
    }
    return false;
}

