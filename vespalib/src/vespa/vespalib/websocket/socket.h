// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>

class FastOS_SocketInterface;

namespace vespalib {
namespace ws {

class Socket
{
private:
    std::unique_ptr<FastOS_SocketInterface> _socket;

public:
    typedef std::unique_ptr<Socket> UP;
    Socket(std::unique_ptr<FastOS_SocketInterface> socket);
    Socket(const vespalib::string &host, int port);
    virtual ~Socket();
    ssize_t read(char *buf, size_t len);
    ssize_t write(const char *buf, size_t len);
};

} // namespace vespalib::ws
} // namespace vespalib
