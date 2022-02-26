// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "generator.h"
#include "request.h"
#include <vbench/core/input_file_reader.h>
#include <vbench/core/handler.h>

namespace vbench {

/**
 * Reads lines from an input file and generates requests that are
 * passed to a request handler.
 **/
class RequestGenerator : public Generator
{
private:
    InputFileReader   _input;
    Handler<Request> &_next;
    bool              _aborted;

public:
    RequestGenerator(const string &inputFile, Handler<Request> &next);
    ~RequestGenerator() override;
    void abort() override;
    void run() override;
    const Taint &tainted() const override { return _input.tainted(); }
};

} // namespace vbench
