// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MessageBusVisitorMessageSession
 *
 * \brief Implementation of a visitor message session using messagebus.
 */
#pragma once

#include <vespa/messagebus/sourcesession.h>
#include <vespa/storage/visiting/visitormessagesession.h>
#include <vespa/storage/visiting/visitorthread.h>
#include <vespa/storage/visiting/visitor.h>

namespace documentapi {
    class DocumentMessage;
}

namespace storage {

class MessageBusVisitorMessageSession : public VisitorMessageSession,
                                        public mbus::IReplyHandler
{
public:
    typedef std::unique_ptr<MessageBusVisitorMessageSession> UP;

    MessageBusVisitorMessageSession(Visitor& visitor, VisitorThread& thread)
        : _visitor(visitor),
          _visitorThread(thread)
    {
    }

    void setSourceSession(mbus::SourceSession::UP sourceSession) {
        _sourceSession = std::move(sourceSession);
    }

    virtual mbus::Result send(std::unique_ptr<documentapi::DocumentMessage> msg) {
        msg->setRetryEnabled(false);
        return _sourceSession->send(std::move(msg));
    }

    /**
       @return Returns the number of pending messages this session has.
    */
    virtual uint32_t pending() {
        return _sourceSession->getPendingCount();
    }

    virtual void handleReply(mbus::Reply::UP reply) {
        _visitorThread.handleMessageBusReply(std::move(reply), _visitor);
    }

private:
    Visitor& _visitor;
    VisitorThread& _visitorThread;
    mbus::SourceSession::UP _sourceSession;
};

} // storage

