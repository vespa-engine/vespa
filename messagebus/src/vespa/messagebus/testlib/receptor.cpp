// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "receptor.h"

namespace mbus {

Receptor::Receptor()
    : IMessageHandler(),
      IReplyHandler(),
      _mon("mbus::Receptor::_mon", true),
      _msg(),
      _reply()
{ }

Receptor::~Receptor() {}

void
Receptor::handleMessage(Message::UP msg)
{
    vespalib::MonitorGuard guard(_mon);
    _msg = std::move(msg);
    guard.broadcast();
}

void
Receptor::handleReply(Reply::UP reply)
{
    vespalib::MonitorGuard guard(_mon);
    _reply = std::move(reply);
    guard.broadcast();
}

Message::UP
Receptor::getMessage(double maxWait)
{
    int ms = (int)(maxWait * 1000);
    FastOS_Time startTime;
    startTime.SetNow();
    vespalib::MonitorGuard guard(_mon);
    while (_msg.get() == 0) {
        int w = ms - (int)startTime.MilliSecsToNow();
        if (w <= 0 || !guard.wait(w)) {
            break;
        }
    }
    return std::move(_msg);
}

Reply::UP
Receptor::getReply(double maxWait)
{
    int ms = (int)(maxWait * 1000);
    FastOS_Time startTime;
    startTime.SetNow();
    vespalib::MonitorGuard guard(_mon);
    while (_reply.get() == 0) {
        int w = ms - (int)startTime.MilliSecsToNow();
        if (w <= 0 || !guard.wait(w)) {
            break;
        }
    }
    return std::move(_reply);
}

} // namespace mbus
