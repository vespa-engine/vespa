// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "message.h"
#include "reply.h"
#include "ireplyhandler.h"
#include "emptyreply.h"
#include "errorcode.h"
#include "error.h"
#include <vespa/vespalib/util/backtrace.h>

#include <vespa/log/log.h>
LOG_SETUP(".message");

namespace mbus {

Message::Message() :
    _route(),
    _timeReceived(),
    _timeRemaining(0),
    _retryEnabled(true),
    _retry(0)
{
    // By observation there are normally 2 handlers pushed.
    getCallStack().reserve(2);
}

Message::~Message()
{
    if (getCallStack().size() > 0) {
        string backtrace = vespalib::getStackTrace(0);
        LOG(warning, "Deleted message %p with non-empty call-stack. Deleted at:\n%s",
            this, backtrace.c_str());
        auto reply = std::make_unique<EmptyReply>();
        swapState(*reply);
        reply->addError(Error(ErrorCode::TRANSIENT_ERROR,
                              "The message object was deleted while containing state information; "
                              "generating an auto-reply."));
        IReplyHandler &handler = reply->getCallStack().pop(*reply);
        handler.handleReply(std::move(reply));
    }
}

void
Message::swapState(Routable &rhs)
{
    Routable::swapState(rhs);
    if (!rhs.isReply()) {
        Message &msg = static_cast<Message&>(rhs);

        std::swap(_route, msg._route);
        std::swap(_retryEnabled, msg._retryEnabled);
        std::swap(_retry, msg._retry);
        std::swap(_timeReceived, msg._timeReceived);
        std::swap(_timeRemaining, msg._timeRemaining);
    }
}

Message &
Message::setTimeReceivedNow()
{
    _timeReceived = vespalib::steady_clock::now();
    return *this;
}

duration
Message::getTimeRemainingNow() const
{
    return std::max(0ns, _timeRemaining - (vespalib::steady_clock::now() - _timeReceived));
}

} // namespace mbus
