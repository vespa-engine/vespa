// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "socket.h"
#include <vespa/fastos/socket.h>

namespace vespalib {
namespace ws {

Socket::Socket(std::unique_ptr<FastOS_SocketInterface> socket)
    : _socket(std::move(socket))
{
}

Socket::Socket(const vespalib::string &host, int port)
    : _socket(new FastOS_Socket())
{
    if (!_socket->SetAddressByHostName(port, host.c_str()) ||
        !_socket->SetSoBlocking(true) ||
        !_socket->Connect())
    {
        _socket->Close();
    }
}

Socket::~Socket()
{
    _socket->Close();
}

ssize_t
Socket::read(char *buf, size_t len)
{
    return _socket->Read(buf, len);
}

ssize_t
Socket::write(const char *buf, size_t len)
{
    return _socket->Write(buf, len);
}

} // namespace vespalib::ws
} // namespace vespalib
