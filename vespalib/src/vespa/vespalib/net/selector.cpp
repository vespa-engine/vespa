// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "selector.h"
#include <cassert>
#include <cstdlib>
#include <cerrno>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>

namespace vespalib {

namespace {

//-----------------------------------------------------------------------------

uint32_t maybe(uint32_t value, bool yes) { return yes ? value : 0; }

void check(int res) {
    if (res == -1) {
        if (errno == ENOMEM) {
            abort();
        }
    }
}

} // namespace vespalib::<unnamed>

//-----------------------------------------------------------------------------

WakeupPipe::WakeupPipe()
    : _pipe()
{
    int res = pipe2(_pipe, O_NONBLOCK);
    assert(res == 0);
}

WakeupPipe::~WakeupPipe()
{
    close(_pipe[0]);
    close(_pipe[1]);
}

void
WakeupPipe::write_token()
{
    char token = 'T';
    [[maybe_unused]] ssize_t res = write(_pipe[1], &token, 1);
}

void
WakeupPipe::read_tokens()
{
    char token_trash[128];
    [[maybe_unused]] ssize_t res = read(_pipe[0], token_trash, sizeof(token_trash));
}

//-----------------------------------------------------------------------------

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

//-----------------------------------------------------------------------------

} // namespace vespalib
