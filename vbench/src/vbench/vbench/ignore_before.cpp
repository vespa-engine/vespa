// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ignore_before.h"

namespace vbench {

IgnoreBefore::IgnoreBefore(double time, Handler<Request> &next)
    : _next(next),
      _time(time),
      _ignored(0)
{
}

void
IgnoreBefore::handle(Request::UP request)
{
    if (request->startTime() < _time) {
        ++_ignored;
        return;
    }
    _next.handle(std::move(request));
}

void
IgnoreBefore::report()
{
    fprintf(stdout, "ignored %zu requests\n", _ignored);
}

} // namespace vbench
