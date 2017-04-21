// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket_handle.h"
#include <sys/socket.h>
#include <errno.h>

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

} // namespace vespalib
