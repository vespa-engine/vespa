// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vbench/core/stream.h>
#include <vespa/fastos/serversocket.h>

namespace vbench {

/**
 * Simple server socket listening to a random port.
 **/
class ServerSocket
{
private:
    FastOS_ServerSocket _serverSocket;
    volatile bool _closed;

public:
    ServerSocket();
    Stream::UP accept();
    int port() { return _serverSocket.GetLocalPort(); }
    void close() { _closed = true; }
};

} // namespace vbench

