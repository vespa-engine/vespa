// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "emulated_epoll.h"
#include <chrono>
#include <vector>

namespace vespalib {

namespace {

uint32_t maybe(uint32_t value, bool yes) { return yes ? value : 0; }

}

Epoll::Epoll()
    : _monitored_lock(),
      _wakeup(),
      _monitored()
{
}

Epoll::~Epoll() = default;

void
Epoll::add(int fd, void *ctx, bool read, bool write)
{
    epoll_event evt;
    evt.events = maybe(EPOLLIN, read) | maybe(EPOLLOUT, write);
    evt.data.ptr = ctx;
    std::lock_guard guard(_monitored_lock);
    _monitored[fd] = evt;
    _wakeup.write_token();
}

void
Epoll::update(int fd, void *ctx, bool read, bool write)
{
    epoll_event evt;
    evt.events = maybe(EPOLLIN, read) | maybe(EPOLLOUT, write);
    evt.data.ptr = ctx;
    std::lock_guard guard(_monitored_lock);
    _monitored[fd] = evt;
    _wakeup.write_token();
}

void
Epoll::remove(int fd)
{
    std::lock_guard guard(_monitored_lock);
    _monitored.erase(fd);
    _wakeup.write_token();
}

size_t
Epoll::wait(epoll_event *events, size_t max_events, int timeout_ms)
{
    size_t evidx = 0;
    std::vector<pollfd> fds;
    auto entryTime = std::chrono::steady_clock::now();
    int timeout_ms_remaining = timeout_ms;
    while (evidx == 0) {
        {
            std::lock_guard guard(_monitored_lock);
            fds.resize(_monitored.size() + 1);
            fds[0].fd = _wakeup.get_read_fd();
            fds[0].events = POLLIN;
            fds[0].revents = 0;
            size_t fdidx = 1;
            for (const auto &mon : _monitored) {
                fds[fdidx].fd = mon.first;
                fds[fdidx].events = mon.second.events;
                fds[fdidx].revents = 0;
                ++fdidx;
            }
        }
        int res = poll(&fds[0], fds.size(), timeout_ms_remaining);
        if (res > 0) {
            std::lock_guard guard(_monitored_lock);
            for (size_t fdidx = 1; fdidx < fds.size() && evidx < max_events; ++fdidx) {
                if (fds[fdidx].revents != 0) {
                    int fd = fds[fdidx].fd;
                    auto monitr = _monitored.find(fd);
                    if (monitr != _monitored.end()) {
                        events[evidx].events = fds[fdidx].revents;
                        events[evidx].data.ptr = monitr->second.data.ptr;
                        ++evidx;
                    }
                }
            }
            if (fds[0].revents != 0) { // Internal epoll emulation wakeup
                _wakeup.read_tokens();
                auto retryTime = std::chrono::steady_clock::now();
                auto delay = std::chrono::duration_cast<std::chrono::milliseconds>(retryTime - entryTime).count();
                timeout_ms_remaining = timeout_ms - delay;
                if (timeout_ms_remaining <= 0) {
                    return evidx; // return current events, or timeout
                }
            }
        } else {
            return 0; // timeout
        }
    }
    return evidx;
}

}
