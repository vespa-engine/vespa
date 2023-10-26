// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "request_dumper.h"

namespace vbench {

RequestDumper::RequestDumper()
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
