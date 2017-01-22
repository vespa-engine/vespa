// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "socket.h"

namespace vbench {

constexpr size_t READ_SIZE = 32768;

Socket::Socket(std::unique_ptr<FastOS_SocketInterface> socket)
    : _socket(std::move(socket)),
      _input(),
      _output(),
      _taint(),
      _eof(false)
{
}

Socket::Socket(const string host, int port)
    : _socket(new FastOS_Socket()),
      _input(),
      _output(),
      _taint(),
      _eof(false)
{
    if (!_socket->SetAddressByHostName(port, host.c_str()) ||
        !_socket->SetSoBlocking(true) ||
        !_socket->Connect() ||
        !_socket->SetSoLinger(false, 0))
    {
        _socket->Close();
        _taint.reset(strfmt("socket connect failed: host: %s, port: %d",
                            host.c_str(), port));
    }
}

Socket::~Socket()
{
    _socket->Close();
}

Memory
Socket::obtain()
{
    if ((_input.get().size == 0) && !_eof && !_taint) {
        WritableMemory buf = _input.reserve(READ_SIZE);
        ssize_t res = _socket->Read(buf.data, buf.size);
        if (res > 0) {
            _input.commit(res);
        } else if (res < 0) {
            _taint.reset("socket read error");
        } else {
            _eof = true;
        }
    }
    return _input.obtain();
}

Input &
Socket::evict(size_t bytes)
{
    _input.evict(bytes);
    return *this;
}

WritableMemory
Socket::reserve(size_t bytes)
{
    return _output.reserve(bytes);
}

Output &
Socket::commit(size_t bytes)
{
    _output.commit(bytes);
    while ((_output.get().size > 0) && !_taint) {
        Memory buf = _output.obtain();
        ssize_t res = _socket->Write(buf.data, buf.size);
        if (res > 0) {
            _output.evict(res);
        } else {
            _taint.reset("socket write error");
        }
    }
    return *this;
}

} // namespace vbench
