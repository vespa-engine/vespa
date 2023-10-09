// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/storageserver/testvisitormessagesession.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/vespalib/util/exceptions.h>

namespace storage {

TestVisitorMessageSession::~TestVisitorMessageSession()
{
}

TestVisitorMessageSession::TestVisitorMessageSession(VisitorThread& t,
                                                     Visitor& v,
                                                     const mbus::Error& autoReplyError,
                                                     bool autoReply)
    : _autoReplyError(autoReplyError),
      _autoReply(autoReply),
      thread(t),
      visitor(v),
      pendingCount(0)
{
}

void
TestVisitorMessageSession::reply(mbus::Reply::UP rep) {
    {
        std::lock_guard guard(_waitMonitor);
        pendingCount--;
    }
    thread.handleMessageBusReply(std::move(rep), visitor);
}

mbus::Result
TestVisitorMessageSession::send(std::unique_ptr<documentapi::DocumentMessage> message)
{
    std::lock_guard guard(_waitMonitor);
    if (_autoReply) {
        pendingCount++;
        mbus::Reply::UP rep = message->createReply();
        rep->setMessage(mbus::Message::UP(message.release()));
        if (_autoReplyError.getCode() == mbus::ErrorCode::NONE) {
            reply(std::move(rep));
            return mbus::Result();
        } else {
            return mbus::Result(_autoReplyError, std::move(message));
        }
    } else {
        pendingCount++;
        sentMessages.push_back(std::move(message));
        _waitCond.notify_all();
        return mbus::Result();
    }
}

void
TestVisitorMessageSession::waitForMessages(unsigned int msgCount) {
    framework::defaultimplementation::RealClock clock;
    vespalib::steady_time endTime = clock.getMonotonicTime() + 60s;

    std::unique_lock guard(_waitMonitor);
    while (sentMessages.size() < msgCount) {
        if (clock.getMonotonicTime() > endTime) {
            throw vespalib::IllegalStateException(
                    vespalib::make_string("Timed out waiting for %u messages "
                                          "in test visitor session", msgCount),
                    VESPA_STRLOC);
        }
        _waitCond.wait_for(guard, 1s);
    }
};

}
