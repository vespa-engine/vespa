// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "socket.h"
#include <vespa/vespalib/net/socket_options.h>
#include <vespa/vespalib/net/socket_spec.h>

namespace vbench {

namespace {

vespalib::SocketHandle connect(const string &host, int port) {
    return vespalib::SocketSpec::from_host_port(host, port).client_address().connect();
}

} // namespace vbench::<unnamed>

constexpr size_t READ_SIZE = 32768;

Socket::Socket(vespalib::SocketHandle socket)
    : _socket(std::move(socket)),
      _input(),
      _output(),
      _taint(),
      _eof(false)
{
}

Socket::Socket(const string &host, int port)
    : _socket(connect(host, port)),
      _input(),
      _output(),
      _taint(),
      _eof(false)
{
    if (!_socket.valid() || !_socket.set_linger(false, 0)) {
        _taint.reset(strfmt("socket connect failed: host: %s, port: %d",
                            host.c_str(), port));
        _socket.reset();
    }
}

Memory
Socket::obtain()
{
    if ((_input.get().size == 0) && !_eof && !_taint) {
        WritableMemory buf = _input.reserve(READ_SIZE);
        ssize_t res = _socket.read(buf.data, buf.size);
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
        ssize_t res = _socket.write(buf.data, buf.size);
        if (res > 0) {
            _output.evict(res);
        } else {
            _taint.reset("socket write error");
        }
    }
    return *this;
}

} // namespace vbench
