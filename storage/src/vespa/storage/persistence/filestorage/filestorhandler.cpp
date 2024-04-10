// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "filestorhandler.h"

namespace storage {

FileStorHandler::LockedMessage::~LockedMessage() = default;

FileStorHandler::LockedMessageBatch::LockedMessageBatch(LockedMessage&& initial_msg)
    : lock(std::move(initial_msg.lock)),
      messages()
{
    if (lock) {
        messages.emplace_back(std::move(initial_msg.msg), std::move(initial_msg.throttle_token));
    }
}

FileStorHandler::LockedMessageBatch::~LockedMessageBatch() = default;

}
