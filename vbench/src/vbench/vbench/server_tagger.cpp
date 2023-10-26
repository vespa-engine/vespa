// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "server_tagger.h"

namespace vbench {

ServerTagger::ServerTagger(const ServerSpec &server,
                           Handler<Request> &next)
    : _server(server),
      _next(next)
{
}

void
ServerTagger::handle(Request::UP request)
{
    request->server(_server);
    _next.handle(std::move(request));
}

} // namespace vbench
