// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vbench/http/server_spec.h>
#include <vbench/core/handler.h>

#include "request.h"
#include "tagger.h"

namespace vbench {

/**
 * Sets the target server for requests.
 **/
class ServerTagger : public Tagger
{
private:
    ServerSpec        _server;
    Handler<Request> &_next;

public:
    ServerTagger(const ServerSpec &server,
                 Handler<Request> &next);
    virtual void handle(Request::UP request) override;
};

} // namespace vbench

