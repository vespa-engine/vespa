// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <sys/epoll.h>

namespace vespalib {

/**
 * The Epoll class is a thin wrapper around the epoll related system
 * calls.
 **/
class Epoll
{
private:
    int _epoll_fd;
public:
    Epoll();
    ~Epoll();
    void add(int fd, void *ctx, bool read, bool write);
    void update(int fd, void *ctx, bool read, bool write);
    void remove(int fd);
    size_t wait(epoll_event *events, size_t max_events, int timeout_ms);
};

}
