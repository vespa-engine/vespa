// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sourcesessionparams.h"
#include "dynamicthrottlepolicy.h"

namespace mbus {

SourceSessionParams::SourceSessionParams() :
    _replyHandler(nullptr),
    _throttlePolicy(std::make_shared<DynamicThrottlePolicy>()),
    _timeout(180s)
{ }

IThrottlePolicy::SP
SourceSessionParams::getThrottlePolicy() const
{
    return _throttlePolicy;
}

SourceSessionParams &
SourceSessionParams::setThrottlePolicy(IThrottlePolicy::SP throttlePolicy)
{
    _throttlePolicy = std::move(throttlePolicy);
    return *this;
}

SourceSessionParams &
SourceSessionParams::setTimeout(duration timeout)
{
    _timeout = timeout;
    return *this;
}

bool
SourceSessionParams::hasReplyHandler() const
{
    return _replyHandler != nullptr;
}

IReplyHandler &
SourceSessionParams::getReplyHandler() const
{
    return *_replyHandler;
}

SourceSessionParams &
SourceSessionParams::setReplyHandler(IReplyHandler &handler)
{
    _replyHandler = &handler;
    return *this;
}

} // namespace mbus
