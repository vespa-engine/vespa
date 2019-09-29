// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket_handle.h"
#include <sys/socket.h>
#include <errno.h>
#include <cassert>
#ifdef __APPLE__
#include <poll.h>
#endif

namespace vespalib {

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
#ifdef __APPLE__
    if (get_blocking()) {
        set_blocking(false);
        while (!_shutdown) {
            pollfd fds;
            fds.fd = _fd;
            fds.events = POLLIN;
            fds.revents = 0;
            int res = poll(&fds, 1, 1);
            if (res < 1 || fds.revents == 0) {
                continue;
            }
            SocketHandle result(::accept(_fd, nullptr, 0));
            if (result.valid() || (errno != EINTR)) {
                set_blocking(true);
                if (result) {
                    result.set_blocking(true);
                }
                return result;
            }
        }
        set_blocking(true);
        SocketHandle result;
        errno = EINVAL;
        return result;
    }
#endif
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
#ifdef __APPLE__
    _shutdown = true;
#endif
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
