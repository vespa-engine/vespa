// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messagedispatcher.h"

#include <vespa/storageapi/message/state.h>
#include <storageapi/messageapi/chainedcommand.h>
#include <storageapi/messageapi/chainedreply.h>
#include <vespa/document/bucket/bucketid.h>

#include <vespa/log/log.h>
LOG_SETUP(".message.dispatcher");

using std::shared_ptr;

namespace storage {

MessageDispatcher::MessageDispatcher(StorageServerInterface& server)
    : StorageLink(),
      _access(),
      _cache(),
      _systemState(""),
      _server(server)
{
}

MessageDispatcher::~MessageDispatcher()
{
    closeNextLink();
    LOG(debug, "Deleting link %s.", toString().c_str());
}

void
MessageDispatcher::onClose()
{
    vespalib::LockGuard lock(_access);
    for (std::map<api::StorageMessage::Id, std::shared_ptr<ReplyPair> >
            ::iterator it = _cache.begin(); it != _cache.end(); ++it)
    {
        std::shared_ptr<api::ChainedReply> reply(it->second->first);
        if (it->second->second != 0) {
            reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED,
                        "Storage node closing down. Aborting command."));
            sendUp(reply);
            it->second->second = 0;
        }
    }

}

void
MessageDispatcher::print(std::ostream& out, bool verbose,
              const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "MessageDispatcher()";
}

bool MessageDispatcher::onDown(const shared_ptr<api::StorageMessage> & msg)
{
    if (msg->getType().isReply()) {
        shared_ptr<api::ChainedReply> reply(
                std::dynamic_pointer_cast<api::ChainedReply>(msg));
        if (reply.get()) {
            return handleReply(reply, false);
        }
    } else {
        shared_ptr<api::ChainedCommand> cmd(
                std::dynamic_pointer_cast<api::ChainedCommand>(msg));
        if (cmd.get()) {
            return handleCommand(cmd);
        }
        if (msg->getType() == api::MessageType::SETSYSTEMSTATE) {
            shared_ptr<api::SetSystemStateCommand> stateCmd(
                    std::dynamic_pointer_cast<api::SetSystemStateCommand>(
                            msg));
            assert(stateCmd.get());
            _systemState = stateCmd->getSystemState();
            LOG(debug, "Got new distributor state %s.",
                _systemState.toString().c_str());
        }
    }
    return false;
}

bool MessageDispatcher::onUp(const std::shared_ptr<api::StorageMessage> & msg)
{
    if (msg->getType().isReply()) {
        shared_ptr<api::ChainedReply> reply(
                std::dynamic_pointer_cast<api::ChainedReply>(msg));
        if (reply.get()) {
            return handleReply(reply, true);
        }
    }
    return false;
}

bool MessageDispatcher::
handleCommand(const std::shared_ptr<api::ChainedCommand> & cmd)
{
    // If we're the first node in the chain,
    // the message has a bucket id related to it,
    // and message came from wrong distributor, fail the message.
    uint16_t expectedNode = 0xFFFF;
    if (cmd->getSourceIndex() != 0xFFFF &&
        cmd->hasBucketId() &&
        !isCorrectDistributor(cmd->getBucketId(), cmd->getSourceIndex(),
                              expectedNode))
    {
        std::string msg;

        if (expectedNode != 0xFFFF) {
            msg = vespalib::make_string(
                    "Got chained command %s with bucket id %s from distributor "
                    "%d, which is wrong given our state. Correct should be %d. "
                    "Ignoring since we're primary node.",
                    cmd->getType().getName().c_str(),
                    cmd->getBucketId().toString().c_str(),
                    cmd->getSourceIndex(),
                    expectedNode);
        } else {
            msg = vespalib::make_string(
                    "Got chained command %s with bucket id %s, but no "
                    "distributors in system state. Haven't received system "
                    "state yet?",
                    cmd->getType().getName().c_str(),
                    cmd->getBucketId().toString().c_str());
        }

        LOG(debug, msg.c_str());
        shared_ptr<api::StorageReply> reply(cmd->makeReply().release());
        reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, msg));
        sendUp(reply);
        return true;

    }
    // If not used chained, just pass it through
    if (!cmd->hasNodes()) {
        LOG(spam, "Chained command contains no nodes, passing it through");
        return false;
    }
    bool runLocally = cmd->getNodes().back()._run;
    // If last node in chain, handle directly
    if (cmd->getNodeCount() == 1) {
        if (runLocally) {
            LOG(spam, "Last node in chain, running it locally.");
            return false;
        } else {
            LOG(spam, "Last node in chain, not running locally, so returning.");
            shared_ptr<api::StorageReply> reply(cmd->makeReply().release());
            sendUp(reply);
            return true;
        }
    }
    // Create commands first, as we need ids for cache.
    shared_ptr<api::ChainedCommand> extCmd(cmd->clone());
    shared_ptr<api::ChainedCommand> localCmd(runLocally ? cmd->clone() : 0);

    // When stuff in cache, to be sure it's there when reply comes.
    shared_ptr<api::ChainedReply> reply(dynamic_cast<api::ChainedReply*>(
                                                cmd->makeReply().release()));
    assert(reply.get());
    {
        vespalib::LockGuard lock(_access);
        shared_ptr<ReplyPair> pair(new ReplyPair(reply, runLocally ? 2 : 1));
        _cache[extCmd->getMsgId()] = pair;
        if (localCmd.get()) {
            _cache[localCmd->getMsgId()] = pair;
        }
    }
    // Send external first since it will probably use the most time
    extCmd->setSourceIndex(0xFFFF);
    extCmd->getNodes().pop_back();
    extCmd->setAddress(api::ServerAddress(_server.getClusterName(), "storage", extCmd->getNodes().back()._node));

    LOG(spam, "Sending chained command on to node %d.",
        extCmd->getNodes().back()._node);
    sendUp(extCmd);
    // Send internal copy if run locally flag is set
    if (runLocally) {
        LOG(spam, "Running chained command locally.");
        localCmd->setSourceIndex(0xFFFF);
        sendDown(localCmd);
    }
    return true;
}

bool
MessageDispatcher::handleReply(
        const std::shared_ptr<api::ChainedReply>& reply, bool localSource)
{
        // Ignore replies on their way up in the storage chain, with a
        // destination object set. These are replies on commands not sent
        // locally, thus not replies possibly for the message dispatcher.
    if (localSource && !reply->isLocal()) return false;

    vespalib::LockGuard lock(_access);
    std::map<api::StorageMessage::Id, shared_ptr<ReplyPair> >::iterator it
        = _cache.find(reply->getMsgId());
    if (it == _cache.end()) {
        return false; // Not for us
    }
    if (it->second.get() == 0) {
        LOG(debug, "Reply already sent back (probably due to shutdown)");
        return true; // Already sent
    }
    bool lastReply = (--it->second->second == 0);
    if (!lastReply || localSource) {
        it->second->first->appendState(*reply);
    } else {
        it->second->first->prependState(*reply);
    }
    if (lastReply) {
        LOG(spam, "Last chained reply retrieved, sending original reply.");
        sendUp(it->second->first);
    } else {
        LOG(spam, "Got chained reply, waiting for next");
    }
    _cache.erase(it);
    return true;
}

bool
MessageDispatcher::isCorrectDistributor(
        const document::BucketId& id, uint16_t distributor, uint16_t& expected)
{
    std::vector<uint16_t> distributors;
    (id).getIdealNodes(lib::NodeType::DISTRIBUTOR, _systemState, _server.getBucketIdFactory(), distributors);
    return (distributors.size() > 0 && (expected = distributors[0]) == distributor);
}

} // storage
