// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket_handle.h"
#include <sys/socket.h>
#include <errno.h>
#include <cassert>

namespace vespalib {

SocketHandle::SocketHandle(SocketHandle &&rhs) noexcept
    : _fd(rhs.release())
{}
SocketHandle &
SocketHandle::operator=(SocketHandle &&rhs) noexcept {
    maybe_close(_fd);
    _fd = rhs.release();
    return *this;
}
SocketHandle::~SocketHandle() { maybe_close(_fd); }

ssize_t
SocketHandle::read(char *buf, size_t len)
{
    for (;;) {
        ssize_t result = ::read(_fd, buf, len);
        if ((result >= 0) || (errno != EINTR)) {
            return result;
        }
    }
}

ssize_t
SocketHandle::write(const char *buf, size_t len)
{
    for (;;) {
        ssize_t result = ::write(_fd, buf, len);
        if ((result >= 0) || (errno != EINTR)) {
            return result;
        }
    }
}

SocketHandle
SocketHandle::accept()
{
    for (;;) {
        SocketHandle result(::accept(_fd, nullptr, 0));
        if (result.valid() || (errno != EINTR)) {
            return result;
        }
    }
}

void
SocketHandle::shutdown()
{
    ::shutdown(_fd, SHUT_RDWR);
}

int
SocketHandle::half_close()
{
    return ::shutdown(_fd, SHUT_WR);
}

int
SocketHandle::get_so_error() const
{
    if (!valid()) {
        return EBADF;
    }
    int so_error = 0;
    socklen_t opt_len = sizeof(so_error);
    if (getsockopt(_fd, SOL_SOCKET, SO_ERROR, &so_error, &opt_len) != 0) {
        return errno;
    }
    assert(opt_len == sizeof(so_error));
    return so_error;
}

} // namespace vespalib
