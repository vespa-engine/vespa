// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "priority_flush_token.h"

namespace proton {

PriorityFlushToken::PriorityFlushToken(std::promise<void> promise)
    : _promise(std::move(promise))
{
}

PriorityFlushToken::~PriorityFlushToken()
{
    _promise.set_value();
}

}
