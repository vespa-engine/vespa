// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "getoperation.h"
#include <vespa/storage/distributor/distributorcomponent.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vdslib/state/nodestate.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback.doc.get");

using document::BucketSpace;

namespace storage::distributor {

GetOperation::GroupId::GroupId(const document::BucketId& id, uint32_t checksum, int node)
    : _id(id),
      _checksum(checksum),
      _node(node)
{
}

bool
GetOperation::GroupId::operator<(const GroupId& other) const
{
    if (_id.getRawId() != other._id.getRawId()) {
        return (_id.getRawId() < other._id.getRawId());
    }
    if (_checksum != other._checksum) {
        return (_checksum < other._checksum);
    }
    if (_node != other._node) {
        return (_node < other._node);
    }
    return false;
}

bool
GetOperation::GroupId::operator==(const GroupId& other) const
{
    return (_id == other._id
            && _checksum == other._checksum
            && _node == other._node);
}

GetOperation::GetOperation(DistributorComponent& manager,
                           DistributorBucketSpace &bucketSpace,
                           std::shared_ptr<api::GetCommand> msg,
                           PersistenceOperationMetricSet& metric)
    : Operation(),
      _manager(manager),
      _bucketSpace(bucketSpace),
      _msg(std::move(msg)),
      _returnCode(api::ReturnCode::OK),
      _doc((document::Document*)NULL),
      _lastModified(0),
      _metric(metric),
      _operationTimer(manager.getClock())
{
    assignTargetNodeGroups();
}

void
GetOperation::onClose(DistributorMessageSender& sender)
{
    _returnCode = api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down");
    sendReply(sender);
}

bool
GetOperation::copyIsOnLocalNode(const BucketCopy& copy) const
{
    return (copy.getNode() == _manager.getIndex());
}

int
GetOperation::findBestUnsentTarget(const GroupVector& candidates) const
{
    int best = -1;
    for (uint32_t i = 0; i < candidates.size(); ++i) {
        if (candidates[i].sent) {
            continue;
        }
        if (copyIsOnLocalNode(candidates[i].copy)) {
            return i; // Can't get better match than this.
        }
        if (best == -1) {
            best = i;
        }
    }
    return best;
}

bool
GetOperation::sendForChecksum(DistributorMessageSender& sender, const document::BucketId& id, GroupVector& res)
{
    const int best = findBestUnsentTarget(res);

    if (best != -1) {
        document::Bucket bucket(_msg->getBucket().getBucketSpace(), id);
        auto command = std::make_shared<api::GetCommand>(bucket, _msg->getDocumentId(),
                                                         _msg->getFieldSet(), _msg->getBeforeTimestamp());
        copyMessageSettings(*_msg, *command);

        LOG(spam, "Sending %s to node %d", command->toString(true).c_str(), res[best].copy.getNode());

        res[best].sent = sender.sendToNode(lib::NodeType::STORAGE, res[best].copy.getNode(), command);
        return true;
    }

    return false;
}

void
GetOperation::onStart(DistributorMessageSender& sender)
{
    // Send one request for each unique group (BucketId/checksum)
    bool sent = false;
    for (std::map<GroupId, GroupVector>::iterator iter = _responses.begin();
         iter != _responses.end(); ++iter)
    {
        sent |= sendForChecksum(sender, iter->first.getBucketId(), iter->second);
    }

    // If nothing was sent (no useful copies), just return NOT_FOUND
    if (!sent) {
        LOG(debug, "No useful bucket copies for get on document %s. Returning without document", _msg->getDocumentId().toString().c_str());
        sendReply(sender);
    }
};

void
GetOperation::onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply>& msg)
{
    api::GetReply* getreply = dynamic_cast<api::GetReply*>(msg.get());
    assert(getreply != nullptr);

    LOG(debug, "Received %s", msg->toString(true).c_str());

    _msg->getTrace().getRoot().addChild(getreply->getTrace().getRoot());
    bool allDone = true;
    for (std::map<GroupId, GroupVector>::iterator iter = _responses.begin();
         iter != _responses.end(); ++iter)
    {
        for (uint32_t i = 0; i < iter->second.size(); i++) {
            if (iter->second[i].sent == getreply->getMsgId()) {
                LOG(debug, "Get on %s returned %s",
                    _msg->getDocumentId().toString().c_str(),
                    getreply->getResult().toString().c_str());

                iter->second[i].received = true;
                iter->second[i].returnCode = getreply->getResult();

                if (getreply->getResult().success()) {
                    if (getreply->getLastModifiedTimestamp() > _lastModified) {
                        _returnCode = getreply->getResult();
                        _lastModified = getreply->getLastModifiedTimestamp();
                        _doc = getreply->getDocument();
                    }
                } else {
                    if (_lastModified == 0) {
                        _returnCode = getreply->getResult();
                    }

                    // Try to send to another node in this checksum group.
                    bool sent = sendForChecksum(sender, iter->first.getBucketId(), iter->second);
                    if (sent) {
                        allDone = false;
                    }
                }
            }

            if (iter->second[i].sent && !iter->second[i].received) {
                LOG(spam, "Have not received all replies yet, setting allDone = false");
                allDone = false;
            }
        }
    }

    if (allDone) {
        LOG(debug, "Get on %s done, returning reply %s",
            _msg->getDocumentId().toString().c_str(), _returnCode.toString().c_str());
        sendReply(sender);
    }
}

void
GetOperation::sendReply(DistributorMessageSender& sender)
{
    if (_msg.get()) {
        auto repl = std::make_shared<api::GetReply>(*_msg, _doc, _lastModified);
        repl->setResult(_returnCode);

        if (_returnCode.success()) {
            ++_metric.ok;
        } else if (_returnCode.getResult() == api::ReturnCode::TIMEOUT) {
            ++_metric.failures.timeout;
        } else if (_returnCode.isBusy()) {
            ++_metric.failures.busy;
        } else if (_returnCode.isNodeDownOrNetwork()) {
            ++_metric.failures.notconnected;
        } else {
            ++_metric.failures.storagefailure;
        }

        if (!_doc.get()) {
            ++_metric.failures.notfound;
        }

        _metric.latency.addValue(_operationTimer.getElapsedTimeAsDouble());

        sender.sendReply(repl);
        _msg.reset();
    }

}

void
GetOperation::assignTargetNodeGroups()
{
    document::BucketIdFactory bucketIdFactory;
    document::BucketId bid = bucketIdFactory.getBucketId(_msg->getDocumentId());

    std::vector<BucketDatabase::Entry> entries;
    _bucketSpace.getBucketDatabase().getParents(bid, entries);

    for (uint32_t j = 0; j < entries.size(); ++j) {
        const BucketDatabase::Entry& e = entries[j];

        LOG(spam, "Entry for %s: %s", e.getBucketId().toString().c_str(),
            e->toString().c_str());

        bool haveTrusted = false;
        for (uint32_t i = 0; i < e->getNodeCount(); i++) {
            const BucketCopy& c = e->getNodeRef(i);

            if (!c.trusted()) {
                continue;
            }

            _responses[GroupId(e.getBucketId(), c.getChecksum(), -1)].push_back(c);
            haveTrusted = true;
            break;
        }

        if (haveTrusted) {
            continue;
        }

        for (uint32_t i = 0; i < e->getNodeCount(); i++) {
            const BucketCopy& copy = e->getNodeRef(i);

            if (!copy.valid()) {
                _responses[GroupId(e.getBucketId(), copy.getChecksum(), copy.getNode())].push_back(copy);
            } else if (!copy.empty()) {
                _responses[GroupId(e.getBucketId(), copy.getChecksum(), -1)].push_back(copy);
            }
        }
    }
}

bool
GetOperation::hasConsistentCopies() const
{
    return _responses.size() == 1;
}

}
