// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "garbagecollectionoperation.h"
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storageapi/message/removelocation.h>

#include <vespa/log/log.h>

LOG_SETUP(".distributor.operation.idealstate.remove");

using namespace storage::distributor;

GarbageCollectionOperation::GarbageCollectionOperation(const std::string& clusterName, const BucketAndNodes& nodes)
    : IdealStateOperation(nodes),
      _tracker(clusterName)
{}

GarbageCollectionOperation::~GarbageCollectionOperation() { }

void
GarbageCollectionOperation::onStart(DistributorMessageSender& sender)
{
    BucketDatabase::Entry entry = _manager->getDistributorComponent().getBucketDatabase().get(getBucketId());
    std::vector<uint16_t> nodes = entry->getNodes();

    for (uint32_t i = 0; i < nodes.size(); i++) {
        std::shared_ptr<api::RemoveLocationCommand> command(
                new api::RemoveLocationCommand(
                        _manager->getDistributorComponent().getDistributor().getConfig().getGarbageCollectionSelection(),
                        getBucketId()));

        command->setPriority(_priority);
        _tracker.queueCommand(command, nodes[i]);
    }

    _tracker.flushQueue(sender);

    if (_tracker.finished()) {
        done();
    }
}

void
GarbageCollectionOperation::onReceive(DistributorMessageSender&,
                           const std::shared_ptr<api::StorageReply>& reply)
{
    api::RemoveLocationReply* rep =
        dynamic_cast<api::RemoveLocationReply*>(reply.get());

    uint16_t node = _tracker.handleReply(*rep);

    if (!rep->getResult().failed()) {
        _manager->getDistributorComponent().updateBucketDatabase(
                getBucketId(),
                BucketCopy(_manager->getDistributorComponent().getUniqueTimestamp(),
                           node,
                           rep->getBucketInfo()));
    } else {
        _ok = false;
    }

    if (_tracker.finished()) {
        if (_ok) {
            BucketDatabase::Entry dbentry = _manager->getDistributorComponent().getBucketDatabase().get(getBucketId());
            if (dbentry.valid()) {
                dbentry->setLastGarbageCollectionTime(
                        _manager->getDistributorComponent().getClock().getTimeInSeconds().getTime());
                _manager->getDistributorComponent().getBucketDatabase().update(dbentry);
            }
        }

        done();
    }
}

bool
GarbageCollectionOperation::shouldBlockThisOperation(uint32_t, uint8_t) const
{
    return true;
}
