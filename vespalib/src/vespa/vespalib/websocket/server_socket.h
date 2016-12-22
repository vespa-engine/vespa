// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include "socket.h"
#include <vespa/fastos/serversocket.h>

namespace vespalib {
namespace ws {

class ServerSocket
{
private:
    struct ServerSocketWrapper : public FastOS_ServerSocket {
        ServerSocketWrapper(int port) : FastOS_ServerSocket(port, 50) {}
        int get_fd() const { return _socketHandle; }
    };
    ServerSocketWrapper _server_socket;
    volatile bool _closed;

public:
    ServerSocket(int port_in);
    Socket::UP accept();
    void close();
    int port() { return _server_socket.GetLocalPort(); }
    bool is_closed() const { return _closed; }
};

} // namespace vespalib::ws
} // namespace vespalib
