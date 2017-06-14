// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "reply.h"
#include "emptyreply.h"
#include "errorcode.h"
#include "ireplyhandler.h"
#include "message.h"
#include "tracelevel.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/backtrace.h>

#include <vespa/log/log.h>
LOG_SETUP(".reply");

namespace mbus {

Reply::Reply() :
    _errors(),
    _msg(),
    _retryDelay(-1.0)
{
    // empty
}

Reply::~Reply()
{
    if (getCallStack().size() > 0) {
        string backtrace = vespalib::getStackTrace(0);
        LOG(warning, "Deleted reply %p with non-empty call-stack. Deleted at:\n%s",
            this, backtrace.c_str());
        Reply::UP reply(new EmptyReply());
        swapState(*reply);
        reply->addError(Error(ErrorCode::FATAL_ERROR,
                              "The reply object was deleted while containing state information; "
                              "generating an auto-reply."));
        IReplyHandler &handler = reply->getCallStack().pop(*reply);
        handler.handleReply(std::move(reply));
    }
}

void
Reply::swapState(Routable &rhs)
{
    Routable::swapState(rhs);
    if (rhs.isReply()) {
        Reply &reply = static_cast<Reply&>(rhs);

        std::swap(_retryDelay, reply._retryDelay);

        Message::UP msg = std::move(_msg);
        _msg = std::move(reply._msg);
        reply._msg = std::move(msg);

        reply._errors.swap(_errors);
    }
}

bool
Reply::isReply() const
{
    return true;
}

void
Reply::addError(const Error &e)
{
    if (getTrace().shouldTrace(TraceLevel::ERROR)) {
        getTrace().trace(TraceLevel::ERROR, e.toString());
    }
    _errors.push_back(e);
}

bool
Reply::hasFatalErrors() const
{
    for (std::vector<Error>::const_iterator it = _errors.begin();
         it != _errors.end(); ++it)
    {
        if (it->getCode() >= ErrorCode::FATAL_ERROR) {
            return true;
        }
    }
    return false;
}

void
Reply::setMessage(Message::UP msg) {
    _msg = std::move(msg);
}

Message::UP
Reply::getMessage() {
    return std::move(_msg);
}

} // namespace mbus
