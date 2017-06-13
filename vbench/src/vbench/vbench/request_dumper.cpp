// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "request_dumper.h"

namespace vbench {

RequestDumper::RequestDumper(Handler<Request> &next)
    : _next(next)
{
}

void
RequestDumper::handle(Request::UP request)
{
    string dump = request->toString();
    fprintf(stderr, "%s\n", dump.c_str());
}

void
RequestDumper::report()
{
}

} // namespace vbench
