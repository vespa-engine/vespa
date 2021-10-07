// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "handler.h"
#include <vespa/vespalib/net/socket.h>
#include <vespa/vespalib/net/server_socket.h>
#include <thread>
#include <atomic>

namespace vespalib {
namespace ws {

class Acceptor {
private:
    ServerSocket _server_socket;
    std::atomic<bool> _is_closed;
    std::thread _accept_thread;

    void accept_main(Handler<Socket> &socket_handler);

public:
    Acceptor(int port_in, Handler<Socket> &socket_handler);
    ~Acceptor();
    int port() { return _server_socket.address().port(); }
};

} // namespace vespalib::ws
} // namespace vespalib
