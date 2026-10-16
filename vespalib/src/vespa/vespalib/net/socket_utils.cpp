// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket_utils.h"

#include <vespa/check_require.h>

#include <fcntl.h>
#include <sys/socket.h>
#include <unistd.h>

namespace vespalib::socketutils {

void set_blocking(int fd, bool blocking) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (blocking) {
        flags &= ~O_NONBLOCK;
    } else {
        flags |= O_NONBLOCK;
    }
    int res = fcntl(fd, F_SETFL, flags);
    CHECK(res == 0);
}

void nonblocking_pipe(int pipefd[2]) {
    int res = pipe(pipefd);
    CHECK(res == 0);
    set_blocking(pipefd[0], false);
    set_blocking(pipefd[1], false);
}

void nonblocking_socketpair(int domain, int type, int protocol, int socketfd[2]) {
    int res = socketpair(domain, type, protocol, socketfd);
    CHECK(res == 0);
    set_blocking(socketfd[0], false);
    set_blocking(socketfd[1], false);
}

} // namespace vespalib::socketutils
