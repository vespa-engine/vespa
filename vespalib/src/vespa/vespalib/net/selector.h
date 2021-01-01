// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "wakeup_pipe.h"
#ifdef __APPLE__
#include "emulated_epoll.h"
#else
#include "native_epoll.h"
#endif
#include <vector>

namespace vespalib {

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
enum class SelectorDispatchResult {WAKEUP_CALLED, NO_WAKEUP};

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
    SelectorDispatchResult dispatch(Handler &handler) {
        SelectorDispatchResult result = SelectorDispatchResult::NO_WAKEUP;
        for (const auto &evt: _events) {
            if (evt.data.ptr == nullptr) {
                _wakeup_pipe.read_tokens();
                handler.handle_wakeup();
                result = SelectorDispatchResult::WAKEUP_CALLED;
            } else {
                Context &ctx = *((Context *)(evt.data.ptr));
                bool read = ((evt.events & (EPOLLIN  | EPOLLERR | EPOLLHUP)) != 0);
                bool write = ((evt.events & (EPOLLOUT  | EPOLLERR | EPOLLHUP)) != 0);
                handler.handle_event(ctx, read, write);
            }
        }
        return result;
    }
};

//-----------------------------------------------------------------------------

/**
 * Selector used to wait for events on a single file
 * descriptor. Useful for testing or sync wrappers. Note: do not use
 * for performance-critical code.
 **/
class SingleFdSelector
{
private:
    int _fd;
    Selector<int> _selector;

public:
    SingleFdSelector(int fd);
    ~SingleFdSelector();

    // returns true when readable or false on wakeup
    bool wait_readable();

    // returns true when writable or false on wakeup
    bool wait_writable();

    // make wait_readable/wait_writable return false immediately
    void wakeup();
};

//-----------------------------------------------------------------------------

} // namespace vespalib
