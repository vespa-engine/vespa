// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "analyzer.h"

namespace vbench {

/**
 * Dumps the textual representation of a request to standard
 * output. Intended for debugging purposes.
 **/
class RequestDumper : public Analyzer
{
private:
    Handler<Request> &_next;

public:
    RequestDumper(Handler<Request> &_next);
    void handle(Request::UP request) override;
    void report() override;
};

} // namespace vbench
