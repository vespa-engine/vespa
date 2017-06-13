// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
        if (frame.discardHandler != NULL) {
            frame.discardHandler->handleDiscard(frame.ctx);
        }
        _stack.pop_back();
    }
}

CallStack::CallStack() : _stack() { }
CallStack::~CallStack() { }
void
CallStack::swap(CallStack &dst)
{
    _stack.swap(dst._stack);
}

void
CallStack::push(IReplyHandler &replyHandler, Context ctx,
                IDiscardHandler *discardHandler)
{
    Frame frame;
    frame.replyHandler = &replyHandler;
    frame.discardHandler = discardHandler;
    frame.ctx = ctx;
    _stack.push_back(frame);
}

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
