// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationowner.h"
#include <vespa/storage/distributor/operations/operation.h>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/storageframework/generic/clock/clock.h>

#include <vespa/log/log.h>
LOG_SETUP(".operationowner");

namespace storage::distributor {

OperationOwner::~OperationOwner() = default;

void
OperationOwner::Sender::sendCommand(const std::shared_ptr<api::StorageCommand> & msg)
{
    _owner.getSentMessageMap().insert(msg->getMsgId(), _cb);
    _sender.sendCommand(msg);
}

void
OperationOwner::Sender::sendReply(const std::shared_ptr<api::StorageReply> & msg)
{
    _sender.sendReply(msg);
};

bool
OperationOwner::handleReply(const std::shared_ptr<api::StorageReply>& reply)
{
    std::shared_ptr<Operation> cb = _sentMessageMap.pop(reply->getMsgId());

    if (cb) {
        Sender sender(*this, _sender, cb);
        cb->receive(sender, reply);
        return true;
    }

    return false;
}

bool
OperationOwner::start(const std::shared_ptr<Operation>& operation, Priority)
{
    LOG(spam, "Starting operation %s", operation->toString().c_str());
    Sender sender(*this, _sender, operation);
    operation->start(sender, _clock.getSystemTime());
    return true;
}

std::string
OperationOwner::toString() const
{
    return _sentMessageMap.toString();
}

void
OperationOwner::onClose()
{
    while (true) {
        std::shared_ptr<Operation> cb = _sentMessageMap.pop();

        if (cb) {
            Sender sender(*this, _sender, std::shared_ptr<Operation>());
            cb->onClose(sender);
        } else {
            break;
        }
    }
}

void
OperationOwner::erase(api::StorageMessage::Id msgId)
{
    _sentMessageMap.pop(msgId);
}

}
