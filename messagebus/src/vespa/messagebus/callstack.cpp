// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "callstack.h"
#include "message.h"
#include "reply.h"
#include "idiscardhandler.h"
#include <cassert>

namespace mbus {

void
CallStack::discard()
{
    while (!_stack.empty()) {
        const Frame &frame = _stack.back();
        if (frame.discardHandler != nullptr) {
            frame.discardHandler->handleDiscard(frame.ctx);
        }
        _stack.pop_back();
    }
}

CallStack::~CallStack() = default;

IReplyHandler &
CallStack::pop(Reply &reply)
{
    assert(!_stack.empty());
    const Frame &frame = _stack.back();
    IReplyHandler *handler = frame.replyHandler;
    reply.setContext(frame.ctx);
    _stack.pop_back();
    return *handler;
}

} // namespace mbus
