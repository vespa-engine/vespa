// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "qps_tagger.h"

namespace vbench {

QpsTagger::QpsTagger(double qps, Handler<Request> &next)
    : _invQps(1.0/qps),
      _count(0),
      _next(next)
{
}

void
QpsTagger::handle(Request::UP request)
{
    request->scheduledTime(((double)(_count++)) * _invQps);
    _next.handle(std::move(request));
}

} // namespace vbench
