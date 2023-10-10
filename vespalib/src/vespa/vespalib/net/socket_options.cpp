// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket_options.h"

#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

namespace vespalib {

namespace {

bool set_bool_opt(int fd, int level, int name, bool value) {
    int data = value;
    return (setsockopt(fd, level, name, &data, sizeof(data)) == 0);
}

} // namespace vespalib::<unnamed>

bool
SocketOptions::set_blocking(int fd, bool value)
{
    int flags = fcntl(fd, F_GETFL, NULL);
    if (flags != -1) {
        if (value) {
            flags &= ~O_NONBLOCK; // clear non-blocking flag
        } else {
            flags |= O_NONBLOCK; // set non-blocking flag
        }
        return (fcntl(fd, F_SETFL, flags) == 0);
    }
    return false;
}

bool
SocketOptions::set_nodelay(int fd, bool value)
{
    return set_bool_opt(fd, IPPROTO_TCP, TCP_NODELAY, value);
}

bool
SocketOptions::set_reuse_addr(int fd, bool value)
{
    return set_bool_opt(fd, SOL_SOCKET, SO_REUSEADDR, value);
}

bool
SocketOptions::set_ipv6_only(int fd, bool value)
{
    return set_bool_opt(fd, IPPROTO_IPV6, IPV6_V6ONLY, value);
}

bool
SocketOptions::set_keepalive(int fd, bool value)
{
    return set_bool_opt(fd, SOL_SOCKET, SO_KEEPALIVE, value);
}

bool
SocketOptions::set_linger(int fd, bool enable, int value)
{
    struct linger data;
    memset(&data, 0, sizeof(data));
    data.l_onoff = enable;
    data.l_linger = value;
    return (setsockopt(fd, SOL_SOCKET, SO_LINGER, &data, sizeof(data)) == 0);
}

} // namespace vespalib
