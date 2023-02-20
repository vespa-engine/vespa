// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "socket_options.h"
#include <unistd.h>

namespace vespalib {

/**
 * Thin wrapper around a socket file descriptor handling
 * ownership. The destructor will close the underlying descriptor if
 * it was valid.
 **/
class SocketHandle
{
private:
    int _fd;

    static void maybe_close(int fd) {
        if (fd >= 0) {
            close(fd);
        }
    }

public:
    SocketHandle() : _fd(-1) {}
    explicit SocketHandle(int sockfd) : _fd(sockfd) {}
    SocketHandle(const SocketHandle &) = delete;
    SocketHandle &operator=(const SocketHandle &) = delete;
    SocketHandle(SocketHandle &&rhs) noexcept;
    SocketHandle &operator=(SocketHandle &&rhs) noexcept;
    ~SocketHandle();
    [[nodiscard]] bool valid() const { return (_fd >= 0); }
    explicit operator bool() const { return valid(); }
    [[nodiscard]] int get() const { return _fd; }
    int release() {
        int old_fd = _fd;
        _fd = -1;
        return old_fd;
    }
    void reset(int fd = -1) {
        maybe_close(_fd);
        _fd = fd;
    }

    bool set_blocking(bool value) { return SocketOptions::set_blocking(_fd, value); }
    bool set_nodelay(bool value) { return SocketOptions::set_nodelay(_fd, value); }
    bool set_reuse_addr(bool value) { return SocketOptions::set_reuse_addr(_fd, value); }
    bool set_ipv6_only(bool value) { return SocketOptions::set_ipv6_only(_fd, value); }
    bool set_keepalive(bool value) { return SocketOptions::set_keepalive(_fd, value); }
    bool set_linger(bool enable, int value) { return SocketOptions::set_linger(_fd, enable, value); }

    ssize_t read(char *buf, size_t len);
    ssize_t write(const char *buf, size_t len);
    SocketHandle accept();
    void shutdown();
    int half_close();
    [[nodiscard]] int get_so_error() const;
};

} // namespace vespalib
