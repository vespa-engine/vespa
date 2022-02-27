// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "request_generator.h"

namespace vbench {

RequestGenerator::RequestGenerator(const string &inputFile,
                                   Handler<Request> &next)
    : _input(inputFile),
      _next(next),
      _aborted(false)
{
}

RequestGenerator::~RequestGenerator() = default;

void
RequestGenerator::abort()
{
    _aborted = true;
}

void
RequestGenerator::run()
{
    string line;
    while (!_aborted && _input.readLine(line)) {
        Request::UP request(new Request());
        request->url(line);
        _next.handle(std::move(request));
    }
}

} // namespace vbench
