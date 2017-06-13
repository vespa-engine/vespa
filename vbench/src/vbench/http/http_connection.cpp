// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "http_connection.h"

namespace vbench {

HttpConnection::HttpConnection(const ServerSpec &s)
    : _server(s),
      _socket(s.host, s.port),
      _lastUsed(-1000.0)
{
}

bool
HttpConnection::mayReuse(double now) const
{
    return (((now - _lastUsed) < 1.0) &&
            !_socket.eof() &&
            !_socket.tainted());
}

} // namespace vbench
