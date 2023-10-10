// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tagger.h"
#include <vbench/http/server_spec.h>

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
    ServerTagger(const ServerSpec &server, Handler<Request> &next);
    void handle(Request::UP request) override;
};

} // namespace vbench
