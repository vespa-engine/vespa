// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket.h"
#include "socket_spec.h"

namespace vespalib {

std::unique_ptr<SimpleSocket>
SimpleSocket::connect(const SocketSpec &spec)
{
    return std::make_unique<SimpleSocket>(spec.client_address().connect());
}

} // namespace vespalib
