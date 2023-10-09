// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "server_socket.h"
#include "socket_spec.h"
#include <sys/stat.h>
#include <dirent.h>
#include <errno.h>
#include <chrono>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.server_socket");

namespace vespalib {

namespace {

SocketHandle adjust_blocking(SocketHandle handle, bool value) {
    if (handle.valid() && handle.set_blocking(value)) {
        return handle;
    } else {
        return SocketHandle();
    }
}

bool is_blocked(int err) { return ((err == EWOULDBLOCK) || (err == EAGAIN)); }

bool is_socket(const vespalib::string &path) {
    struct stat info;
    if (path.empty() || (lstat(path.c_str(), &info) != 0)) {
        return false;
    }
    return S_ISSOCK(info.st_mode);
}

}

void
ServerSocket::cleanup()
{
    if (valid() && is_socket(_path)) {
        unlink(_path.c_str());
    }
}

ServerSocket::ServerSocket(const SocketSpec &spec)
    : _handle(adjust_blocking(spec.server_address().listen(), false)),
      _path(spec.path()),
      _blocking(true),
      _shutdown(false)
{
    if (!_handle.valid() && is_socket(_path)) {
        if (!spec.client_address().connect_async().valid()) {
            LOG(warning, "removing old socket: '%s'", _path.c_str());
            unlink(_path.c_str());
            _handle = spec.server_address().listen();
        }
    }
    if (!_handle.valid()) {
        LOG(warning, "listen failed: '%s'", spec.spec().c_str());
    }
}

ServerSocket::ServerSocket(const vespalib::string &spec)
    : ServerSocket(SocketSpec(spec))
{
}

ServerSocket::ServerSocket(int port)
    : ServerSocket(SocketSpec::from_port(port))
{
}

ServerSocket::ServerSocket(ServerSocket &&rhs)
    : _handle(std::move(rhs._handle)),
      _path(std::move(rhs._path)),
      _blocking(rhs._blocking),
      _shutdown(rhs._shutdown.load(std::memory_order_acquire))
{
    rhs._path.clear();
}

ServerSocket &
ServerSocket::operator=(ServerSocket &&rhs)
{
    cleanup();
    _handle = std::move(rhs._handle);
    _path = std::move(rhs._path);
    _blocking = rhs._blocking;
    _shutdown.store(rhs._shutdown.load(std::memory_order_acquire), std::memory_order_release);
    rhs._path.clear();
    return *this;
}

SocketAddress
ServerSocket::address() const
{
    return SocketAddress::address_of(_handle.get());
}

void
ServerSocket::shutdown()
{
    _shutdown.store(true, std::memory_order_release);
    _handle.shutdown();
}

SocketHandle
ServerSocket::accept()
{
    if (!_blocking) {
        return adjust_blocking(_handle.accept(), true);
    } else {
        for (;;) {
            if (_shutdown.load(std::memory_order_acquire)) {
                errno = EIO;
                return SocketHandle();
            }
            SocketHandle res = _handle.accept();
            if (res.valid() || !is_blocked(errno)) {
                return adjust_blocking(std::move(res), true);
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
    }
}

} // namespace vespalib
