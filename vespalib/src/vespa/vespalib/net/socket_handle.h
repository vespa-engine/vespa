// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#ifdef __APPLE__
    volatile bool _shutdown;
#endif

    static void maybe_close(int fd) {
        if (fd >= 0) {
            close(fd);
        }
    }

public:
#ifdef __APPLE__
    SocketHandle() : _fd(-1), _shutdown(false) {}
    explicit SocketHandle(int sockfd) : _fd(sockfd), _shutdown(false) {}
#else
    SocketHandle() : _fd(-1) {}
    explicit SocketHandle(int sockfd) : _fd(sockfd) {}
#endif
    SocketHandle(const SocketHandle &) = delete;
    SocketHandle &operator=(const SocketHandle &) = delete;
#ifdef __APPLE__
    SocketHandle(SocketHandle &&rhs) : _fd(rhs.release()), _shutdown(rhs._shutdown) {}
#else
    SocketHandle(SocketHandle &&rhs) : _fd(rhs.release()) {}
#endif
    SocketHandle &operator=(SocketHandle &&rhs) {
        maybe_close(_fd);
        _fd = rhs.release();
        return *this;
    }
    ~SocketHandle() { maybe_close(_fd); }
    bool valid() const { return (_fd >= 0); }
    operator bool() const { return valid(); }
    int get() const { return _fd; }
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
    bool get_blocking() { return SocketOptions::get_blocking(_fd); }
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
    int get_so_error() const;
};

} // namespace vespalib
