// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "request_sink.h"

namespace vbench {

RequestSink::RequestSink()
{
}

void
RequestSink::handle(Request::UP request)
{
    request.reset();
}

void
RequestSink::report()
{
}

} // namespace vbench
