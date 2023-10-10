// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MessageBusVisitorMessageSession
 *
 * \brief Implementation of a visitor message session using messagebus.
 */
#pragma once

#include "visitormessagesession.h"
#include "visitorthread.h"
#include "visitor.h"
#include <vespa/messagebus/sourcesession.h>

namespace documentapi {
    class DocumentMessage;
}

namespace storage {

class MessageBusVisitorMessageSession : public VisitorMessageSession,
                                        public mbus::IReplyHandler
{
public:
    using UP = std::unique_ptr<MessageBusVisitorMessageSession>;

    MessageBusVisitorMessageSession(Visitor& visitor, VisitorThread& thread)
        : _visitor(visitor),
          _visitorThread(thread)
    {
    }

    void setSourceSession(mbus::SourceSession::UP sourceSession) {
        _sourceSession = std::move(sourceSession);
    }

    mbus::Result send(std::unique_ptr<documentapi::DocumentMessage> msg) override {
        msg->setRetryEnabled(false);
        return _sourceSession->send(std::move(msg));
    }

    uint32_t pending() override {
        return _sourceSession->getPendingCount();
    }

    void handleReply(mbus::Reply::UP reply) override {
        _visitorThread.handleMessageBusReply(std::move(reply), _visitor);
    }

private:
    Visitor& _visitor;
    VisitorThread& _visitorThread;
    mbus::SourceSession::UP _sourceSession;
};

} // storage
