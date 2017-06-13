// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <sys/epoll.h>

namespace vespalib {

//-----------------------------------------------------------------------------

/**
 * A wakeup pipe is a non-blocking pipe that is used to wake up a
 * blocking call to epoll_wait. The pipe readability is part of the
 * selection set and a wakeup is triggered by writing to the
 * pipe. When a wakeup is detected, pending tokens will be read and
 * discarded to avoid spurious wakeups in the future.
 **/
class WakeupPipe {
private:
    int _pipe[2];
public:
    WakeupPipe();
    ~WakeupPipe();
    int get_read_fd() const { return _pipe[0]; }
    void write_token();
    void read_tokens();
};

//-----------------------------------------------------------------------------

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

//-----------------------------------------------------------------------------

/**
 * Simple class used to hold events extracted from a call to epoll_wait. 
 **/
class EpollEvents
{
private:
    std::vector<epoll_event> _epoll_events;
    size_t                   _num_events;
public:
    EpollEvents(size_t max_events) : _epoll_events(max_events), _num_events(0) {}
    void extract(Epoll &epoll, int timeout_ms) {
        _num_events = epoll.wait(&_epoll_events[0], _epoll_events.size(), timeout_ms);
    }
    const epoll_event *begin() const { return &_epoll_events[0]; }
    const epoll_event *end() const { return &_epoll_events[_num_events]; }
    size_t size() const { return _num_events; }
};

//-----------------------------------------------------------------------------

template <typename Context>
class Selector
{
private:
    Epoll       _epoll;
    WakeupPipe  _wakeup_pipe;
    EpollEvents _events;
public:
    Selector()
        : _epoll(), _wakeup_pipe(), _events(4096)
    {
        _epoll.add(_wakeup_pipe.get_read_fd(), nullptr, true, false);    
    }
    ~Selector() {
        _epoll.remove(_wakeup_pipe.get_read_fd());
    }
    void add(int fd, Context &ctx, bool read, bool write) { _epoll.add(fd, &ctx, read, write); }
    void update(int fd, Context &ctx, bool read, bool write) { _epoll.update(fd, &ctx, read, write); }
    void remove(int fd) { _epoll.remove(fd); }
    void wakeup() { _wakeup_pipe.write_token(); }
    void poll(int timeout_ms) { _events.extract(_epoll, timeout_ms); }
    size_t num_events() const { return _events.size(); }
    template <typename Handler>
    void dispatch(Handler &handler) {
        for (const auto &evt: _events) {
            if (evt.data.ptr == nullptr) {
                _wakeup_pipe.read_tokens();
                handler.handle_wakeup();
            } else {
                Context &ctx = *((Context *)(evt.data.ptr));
                bool read = ((evt.events & (EPOLLIN  | EPOLLERR | EPOLLHUP)) != 0);
                bool write = ((evt.events & (EPOLLOUT  | EPOLLERR | EPOLLHUP)) != 0);
                handler.handle_event(ctx, read, write);
            }
        }
    }
};

//-----------------------------------------------------------------------------

} // namespace vespalib
