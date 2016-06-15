// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

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
    SocketHandle(SocketHandle &&rhs) : _fd(rhs.release()) {}
    SocketHandle &operator=(SocketHandle &&rhs) {
        maybe_close(_fd);
        _fd = rhs.release();
        return *this;
    }
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
    ~SocketHandle() { maybe_close(_fd); }
};

} // namespace vespalib
