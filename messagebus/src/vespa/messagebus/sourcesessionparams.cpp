// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sourcesessionparams.h"
#include "dynamicthrottlepolicy.h"

namespace mbus {

SourceSessionParams::SourceSessionParams() :
    _replyHandler(NULL),
    _throttlePolicy(new DynamicThrottlePolicy()),
    _timeout(180.0)
{ }

IThrottlePolicy::SP
SourceSessionParams::getThrottlePolicy() const
{
    return _throttlePolicy;
}

SourceSessionParams &
SourceSessionParams::setThrottlePolicy(IThrottlePolicy::SP throttlePolicy)
{
    _throttlePolicy = throttlePolicy;
    return *this;
}

double
SourceSessionParams::getTimeout() const
{
    return _timeout;
}

SourceSessionParams &
SourceSessionParams::setTimeout(double timeout)
{
    _timeout = timeout;
    return *this;
}

bool
SourceSessionParams::hasReplyHandler() const
{
    return _replyHandler != NULL;
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
