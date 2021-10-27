// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "wakeup_pipe.h"
#include <poll.h>
#include <map>
#include <mutex>

#define EPOLLERR POLLERR
#define EPOLLHUP POLLHUP
#define EPOLLIN POLLIN
#define EPOLLOUT POLLOUT

namespace vespalib {

// structure describing which event occurred.
struct epoll_event
{
    struct {
        void *ptr;
    } data;
    uint32_t events;
};

/**
 * The Epoll class is a thin wrapper around basic emulation of the epoll
 * related system calls.
 **/
class Epoll
{
private:
    std::mutex _monitored_lock;
    WakeupPipe _wakeup;
    std::map<int, epoll_event> _monitored;
public:
    Epoll();
    ~Epoll();
    void add(int fd, void *ctx, bool read, bool write);
    void update(int fd, void *ctx, bool read, bool write);
    void remove(int fd);
    size_t wait(epoll_event *events, size_t max_events, int timeout_ms);
};

}
