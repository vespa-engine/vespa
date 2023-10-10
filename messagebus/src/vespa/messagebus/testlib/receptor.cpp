// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "receptor.h"

using namespace std::chrono;

namespace mbus {

Receptor::Receptor() = default;
Receptor::~Receptor() = default;

void
Receptor::handleMessage(Message::UP msg)
{
    std::lock_guard guard(_mon);
    _msg = std::move(msg);
    _cond.notify_all();
}

void
Receptor::handleReply(Reply::UP reply)
{
    std::lock_guard guard(_mon);
    _reply = std::move(reply);
    _cond.notify_all();
}

Message::UP
Receptor::getMessage(duration maxWait)
{
    steady_clock::time_point startTime = steady_clock::now();
    std::unique_lock guard(_mon);
    while ( ! _msg) {
        duration w = maxWait - duration_cast<milliseconds>(steady_clock::now() - startTime);
        if (w <= duration::zero() || (_cond.wait_for(guard, w) == std::cv_status::timeout)) {
            break;
        }
    }
    return std::move(_msg);
}

Reply::UP
Receptor::getReply(duration maxWait)
{
    steady_clock::time_point startTime = steady_clock::now();
    std::unique_lock guard(_mon);
    while (_reply.get() == 0) {
        duration w = maxWait - duration_cast<milliseconds>(steady_clock::now() - startTime);
        if (w <= duration::zero() || (_cond.wait_for(guard, w) == std::cv_status::timeout)) {
            break;
        }
    }
    return std::move(_reply);
}

} // namespace mbus
