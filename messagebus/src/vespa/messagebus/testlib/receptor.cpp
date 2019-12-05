// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "receptor.h"

using namespace std::chrono;

namespace mbus {

Receptor::Receptor() = default;
Receptor::~Receptor() = default;

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
Receptor::getMessage(duration maxWait)
{
    steady_clock::time_point startTime = steady_clock::now();
    vespalib::MonitorGuard guard(_mon);
    while (_msg.get() == 0) {
        duration w = maxWait - duration_cast<milliseconds>(steady_clock::now() - startTime);
        if (w <= duration::zero() || !guard.wait(w)) {
            break;
        }
    }
    return std::move(_msg);
}

Reply::UP
Receptor::getReply(duration maxWait)
{
    steady_clock::time_point startTime = steady_clock::now();
    vespalib::MonitorGuard guard(_mon);
    while (_reply.get() == 0) {
        duration w = maxWait - duration_cast<milliseconds>(steady_clock::now() - startTime);
        if (w <= duration::zero() || !guard.wait(w)) {
            break;
        }
    }
    return std::move(_reply);
}

} // namespace mbus
