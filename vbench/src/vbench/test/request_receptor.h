// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vbench/core/handler.h>
#include <vbench/vbench/request.h>

namespace vbench {

struct RequestReceptor : public Handler<Request> {
    Request::UP request;
    RequestReceptor() : request() {}
    ~RequestReceptor() override;
    void handle(Request::UP req) override;
};

} // namespace vbench

