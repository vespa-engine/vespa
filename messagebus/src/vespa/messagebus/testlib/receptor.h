// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/messagebus/imessagehandler.h>
#include <vespa/messagebus/ireplyhandler.h>
#include <vespa/messagebus/message.h>
#include <vespa/messagebus/reply.h>
#include <condition_variable>

namespace mbus {

class Receptor : public IMessageHandler,
                 public IReplyHandler
{
private:
    std::mutex              _mon;
    std::condition_variable _cond;
    Message::UP             _msg;
    Reply::UP               _reply;
public:
    Receptor();
    ~Receptor() override;
    void handleMessage(Message::UP msg) override;
    void handleReply(Reply::UP reply) override;
    Message::UP getMessage(duration maxWait = 120s);
    Reply::UP getReply(duration maxWait = 120s);
    Message::UP getMessageNow() { return getMessage(duration::zero()); }
    Reply::UP getReplyNow() { return getReply(duration::zero()); }
};

} // namespace mbus
