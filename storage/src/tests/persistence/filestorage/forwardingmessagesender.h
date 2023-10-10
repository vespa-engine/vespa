// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/common/storagelink.h>

namespace storage {

/**
 * Simple implementation of MessageSender which simply forwards all messages
 * to a provided storage link.
 */
struct ForwardingMessageSender : public MessageSender {
    StorageLink& link;

    ForwardingMessageSender(StorageLink& l) : link(l) {}

    void sendCommand(const std::shared_ptr<api::StorageCommand> & cmd) override { link.sendUp(cmd); }
    void sendReply(const std::shared_ptr<api::StorageReply> & reply) override { link.sendUp(reply); }
};

} // storage
