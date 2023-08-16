// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messagetracker.h"
#include <vespa/storageapi/messageapi/bucketcommand.h>
#include <vespa/storageapi/messageapi/bucketreply.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".messagetracker");

namespace storage::distributor {

MessageTracker::MessageTracker(const ClusterContext& cluster_context)
  : _cluster_ctx(cluster_context)
{}

MessageTracker::~MessageTracker() = default;

void
MessageTracker::flushQueue(MessageSender& sender)
{
    _sentMessages.resize(_sentMessages.size() + _commandQueue.size());
    for (const auto & toSend : _commandQueue) {
        toSend._msg->setAddress(api::StorageMessageAddress::create(_cluster_ctx.cluster_name_ptr(), lib::NodeType::STORAGE, toSend._target));
        _sentMessages[toSend._msg->getMsgId()] = toSend._target;
        sender.sendCommand(toSend._msg);
    }

    _commandQueue.clear();
}

uint16_t
MessageTracker::handleReply(api::BucketReply& reply)
{
    const auto found = _sentMessages.find(reply.getMsgId());
    if (found == _sentMessages.end()) [[unlikely]] {
        LOG(warning, "Received reply %" PRIu64 " for callback which we have no recollection of", reply.getMsgId());
        return (uint16_t)-1;
    }
    uint16_t node = found->second;
    _sentMessages.erase(found);
    return node;
}

}
