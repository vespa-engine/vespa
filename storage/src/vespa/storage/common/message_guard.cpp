// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "message_guard.h"

namespace storage {

MessageGuard::~MessageGuard() {
    _lock.unlock();
    for (uint32_t i = 0; i < messagesUp.size(); i++) {
        _messageSender.sendUp(messagesUp[i]);
    }
    for (uint32_t i = 0; i < messagesDown.size(); i++) {
        _messageSender.sendDown(messagesDown[i]);
    }
}

}
