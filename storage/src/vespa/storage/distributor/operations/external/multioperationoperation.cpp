// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multioperationoperation.h"
#include "putoperation.h"
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback.doc.multioperation");

using document::BucketSpace;

namespace storage::distributor {

MultiOperationOperation::MultiOperationOperation(
        DistributorComponent& manager,
        DistributorBucketSpace &bucketSpace,
        const std::shared_ptr<api::MultiOperationCommand> & msg,
        PersistenceOperationMetricSet& metric)
    : Operation(),
      _reply(new api::MultiOperationReply(*msg)),
      _trackerInstance(metric, _reply, manager),
      _tracker(_trackerInstance),
      _msg(msg),
      _manager(manager),
      _bucketSpace(bucketSpace),
      _minUseBits(manager.getDistributor().getConfig().getMinimalBucketSplit())
{
}

MultiOperationOperation::~MultiOperationOperation() {}

bool
MultiOperationOperation::sendToBucket(
        BucketDatabase::Entry& e,
        std::shared_ptr<api::MultiOperationCommand> moCommand)
{
    std::vector<uint16_t> targetNodes;
    std::vector<MessageTracker::ToSend> createBucketBatch;

    if (PutOperation::checkCreateBucket(_bucketSpace.getDistribution(),
                                        _bucketSpace.getClusterState(),
                                        e,
                                        targetNodes,
                                        createBucketBatch,
                                        *moCommand))
    {
        _bucketSpace.getBucketDatabase().update(e);
    }

    if (createBucketBatch.size()) {
        _tracker.queueMessageBatch(createBucketBatch);
    }

    std::vector<MessageTracker::ToSend> messages;

    for (uint32_t i = 0; i < targetNodes.size(); i++) {
        std::shared_ptr<api::MultiOperationCommand> snd(
                new api::MultiOperationCommand(*moCommand));
        copyMessageSettings(*moCommand, *snd);
        messages.push_back(MessageTracker::ToSend(snd, targetNodes[i]));
    }

    _tracker.queueMessageBatch(messages);

    return true;
}

typedef std::vector<vdslib::DocumentList::Entry> EntryVector;

uint32_t
MultiOperationOperation::getMinimumUsedBits(const vdslib::DocumentList& opList) const
{
    uint32_t splitBit = 58;
    uint64_t splitMask = 0;
    document::BucketId refBucket;

    for (uint32_t i=0; i< splitBit; ++i) {
        splitMask = (splitMask << 1) | 1;
    }

    //iterate through operations to find which bucketId they belong to
    for (vdslib::DocumentList::const_iterator operationIt = opList.begin();
         operationIt != opList.end();
         operationIt++)
    {
        document::DocumentId docId = operationIt->getDocumentId();
        document::BucketId bucketId(
                _manager.getBucketIdFactory().getBucketId(docId));

        if (refBucket.getRawId() == 0) {
            refBucket = bucketId;
        } else {
            while ((bucketId.getRawId() & splitMask) != (refBucket.getRawId() & splitMask)) {
                --splitBit;
                splitMask = splitMask >> 1;
            }
        }
    }

    return splitBit;
}

namespace {

struct BucketOperationList {
    BucketDatabase::Entry entry;
    EntryVector operations;
};

}

void
MultiOperationOperation::onStart(DistributorMessageSender& sender)
{
    lib::ClusterState systemState = _bucketSpace.getClusterState();

    // Don't do anything if all nodes are down.
    bool up = false;
    for (uint16_t i = 0; i < systemState.getNodeCount(lib::NodeType::STORAGE); i++) {
        if (_manager.storageNodeIsUp(i)) {
            up = true;
            break;
        }
    }

    if (!up) {
        _tracker.fail(sender, api::ReturnCode(api::ReturnCode::NOT_CONNECTED, "Can't perform operations: No storage nodes available"));
        return;
    }

    const vdslib::DocumentList& opList= _msg->getOperations();
    LOG(debug, "Received MultiOperation message with %d operations", opList.size());
    std::map<document::BucketId, BucketOperationList> bucketMap;

    if ((_manager.getDistributor().getConfig().getSplitCount() != 0 && opList.size() > _manager.getDistributor().getConfig().getSplitCount() / 3) ||
        (_manager.getDistributor().getConfig().getSplitSize() != 0 && opList.getBufferSize() > _manager.getDistributor().getConfig().getSplitSize() / 3)) {
        _minUseBits = getMinimumUsedBits(opList);
    }

    //iterate through operations to find which bucketId they belong to
    for (vdslib::DocumentList::const_iterator operationIt = opList.begin();
         operationIt != opList.end();
         operationIt++)
    {
        if (operationIt->valid()) {
            document::DocumentId docId = operationIt->getDocumentId();
            document::Bucket bucket(_msg->getBucket().getBucketSpace(),
                                    _manager.getBucketIdFactory().getBucketId(docId));

            LOG(debug, "Operation with documentid %s mapped to bucket %s", docId.toString().c_str(), bucket.toString().c_str());

            // OK, we have a bucket ID, must now know which buckets this belongs
            // to
            std::vector<BucketDatabase::Entry> entries;
            _bucketSpace.getBucketDatabase().getParents(bucket.getBucketId(), entries);

            if (entries.empty()) {
                entries.push_back(_manager.createAppropriateBucket(bucket));
            }

            for (uint32_t i = 0; i < entries.size(); ++i) {
                bucketMap[entries[i].getBucketId()].entry = entries[i];
                bucketMap[entries[i].getBucketId()].operations.push_back(*operationIt);

                LOG(debug, "Operation with flags %d must go to bucket %s",
                    operationIt->getFlags(), entries[i].toString().c_str());
            }
        }
    }

    LOG(debug,
        "MultiOperation has operations for %lu bucketIds",
        (unsigned long)bucketMap.size());

    uint64_t highestTimestamp = 0;

    //iterate through the map of <bucket, vector<Entry>>
    for (std::map<document::BucketId, BucketOperationList>::iterator bucketIt =
             bucketMap.begin();
         bucketIt != bucketMap.end();
         bucketIt++)
    {
        LOG(debug, "Iterating through bucketMap, bucket %s", bucketIt->first.toString().c_str());
        //get the size of the buffer large enough to hold the entries that
        //must go to this bucketId
        uint32_t blockSize = 4; //4 bytes initially for length

        EntryVector& v = bucketIt->second.operations;
        for (EntryVector::iterator entryIt = v.begin();
             entryIt != v.end();
             entryIt++) {
            blockSize += entryIt->getSerializedSize();
        }
        assert(blockSize > 4);

        document::Bucket bucket(_msg->getBucket().getBucketSpace(), bucketIt->first);
        //now create a MultiOperationCommand with the new DocumentList
        std::shared_ptr<api::MultiOperationCommand>
            command(new api::MultiOperationCommand(
                            _manager.getTypeRepo(),
                            bucket, blockSize));
        copyMessageSettings(*_msg, *command);

        LOG(debug, "Block size %d", blockSize);
        vdslib::WritableDocumentList& block = command->getOperations();

        //iterate through the entries, and add them to the new DocumentList
        for (EntryVector::iterator entryIt = v.begin(); entryIt != v.end(); entryIt++)
        {
            uint64_t ts;
            if(!_msg->keepTimeStamps()){
                ts = _manager.getUniqueTimestamp();
            }
            else{
                ts = entryIt->getTimestamp();
            }

            if (ts > highestTimestamp) {
                highestTimestamp = ts;
            }
            block.addEntry(*entryIt, ts);

            LOG(debug, "Entry size is %d", block.size());
        }

        sendToBucket(bucketIt->second.entry, command);
    }

    _tracker.flushQueue(sender);

    _msg = std::shared_ptr<api::MultiOperationCommand>();
    _reply->setHighestModificationTimestamp(highestTimestamp);
}

void
MultiOperationOperation::onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg)
{
    _tracker.receiveReply(sender, static_cast<api::BucketInfoReply&>(*msg));
}

void
MultiOperationOperation::onClose(DistributorMessageSender& sender)
{
    _tracker.fail(sender, api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down"));
}

}
