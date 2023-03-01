// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "native_epoll.h"
#include <cassert>
#include <cerrno>
#include <cstring>
#include <unistd.h>
#include <vespa/log/log.h>

namespace vespalib {

namespace {

uint32_t maybe(uint32_t value, bool yes) { return yes ? value : 0; }

void check(int res) {
    if (res == -1) {
        if (errno == ENOMEM) {
	    LOG_ABORT("out of memory");
        }
    }
}

}

Epoll::Epoll()
    : _epoll_fd(epoll_create1(0))
{
    assert(_epoll_fd != -1);
}

Epoll::~Epoll()
{
    close(_epoll_fd);
}

void
Epoll::add(int fd, void *ctx, bool read, bool write)
{
    epoll_event evt;
    evt.events = maybe(EPOLLIN, read) | maybe(EPOLLOUT, write);
    evt.data.ptr = ctx;
    check(epoll_ctl(_epoll_fd, EPOLL_CTL_ADD, fd, &evt));
}

void
Epoll::update(int fd, void *ctx, bool read, bool write)
{
    epoll_event evt;
    evt.events = maybe(EPOLLIN, read) | maybe(EPOLLOUT, write);
    evt.data.ptr = ctx;
    check(epoll_ctl(_epoll_fd, EPOLL_CTL_MOD, fd, &evt));
}

void
Epoll::remove(int fd)
{
    epoll_event evt;
    memset(&evt, 0, sizeof(evt));
    check(epoll_ctl(_epoll_fd, EPOLL_CTL_DEL, fd, &evt));
}

size_t
Epoll::wait(epoll_event *events, size_t max_events, int timeout_ms)
{
    int res = epoll_wait(_epoll_fd, events, max_events, timeout_ms);
    return std::max(res, 0);
}

}
