// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <vespa/fastos/fastos.h>
#include "server_socket.h"
#include <vbench/core/socket.h>
#include <vespa/vespalib/util/thread.h>

namespace vbench {

ServerSocket::ServerSocket()
    : _serverSocket(0, 500, 0, 0),
      _closed(false)
{
    _serverSocket.SetSoBlocking(false);
    _serverSocket.Listen();
}

Stream::UP
ServerSocket::accept()
{
    while (!_closed) {
        std::unique_ptr<FastOS_SocketInterface> socket(_serverSocket.Accept());
        if (socket.get() != 0) {
            socket->SetSoBlocking(true);
            return Stream::UP(new Socket(std::move(socket)));
        } else {
            int error = FastOS_Socket::GetLastError();
            if (error == FastOS_Socket::ERR_WOULDBLOCK) {
                vespalib::Thread::sleep(10);
            } else {
                return Stream::UP();
            }
        }
    }
    return Stream::UP();
}

} // namespace vbench
