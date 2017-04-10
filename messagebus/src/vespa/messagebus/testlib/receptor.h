// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/sync.h>
#include <vespa/messagebus/imessagehandler.h>
#include <vespa/messagebus/ireplyhandler.h>

namespace mbus {

class Receptor : public IMessageHandler,
                 public IReplyHandler
{
private:
    vespalib::Monitor _mon;
    Message::UP       _msg;
    Reply::UP         _reply;

    Receptor(const Receptor &);
    Receptor &operator=(const Receptor &);
public:
    Receptor();
    virtual void handleMessage(Message::UP msg) override;
    virtual void handleReply(Reply::UP reply) override;
    Message::UP getMessage(double maxWait = 120.0);
    Reply::UP getReply(double maxWait = 120.0);
};

} // namespace mbus

