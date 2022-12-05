// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "async_io.h"
#include "detached.h"
#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/util/require.h>

#include <atomic>
#include <vector>
#include <map>
#include <set>

namespace vespalib::coro {

namespace {

using Handle = std::coroutine_handle<>;

template <typename F>
struct await_void {
    bool ready;
    F on_suspend;
    await_void(bool ready_in, F on_suspend_in)
      : ready(ready_in), on_suspend(on_suspend_in) {}
    bool await_ready() const noexcept { return ready; }
    auto await_suspend(Handle handle) const { return on_suspend(handle); }
    constexpr void await_resume() noexcept {}
};
template <typename F>
await_void(bool ready_in, F on_suspend_in) -> await_void<F>;

struct SelectorThread : AsyncIo {

    struct FdContext {
        int    _fd;
        bool   _epoll_read;
        bool   _epoll_write;
        Handle _reader;
        Handle _writer;
        FdContext(int fd_in)
          : _fd(fd_in),
            _epoll_read(false), _epoll_write(false),
            _reader(nullptr), _writer(nullptr) {}
    };
    std::map<int,FdContext> _state;
    std::set<int> _check;

    Selector<FdContext> _selector;
    bool                _shutdown;
    std::thread         _thread;
    bool                _check_queue;
    std::vector<Handle> _todo;
    std::mutex          _lock;
    std::vector<Handle> _queue;

    SelectorThread()
      : _state(),
        _check(),
        _selector(),
        _shutdown(false),
        _thread(&SelectorThread::main, this),
        _check_queue(false),
        _todo(),
        _lock(),
        _queue() {}
    void main();
    ~SelectorThread();
    bool is_my_thread() const { return (std::this_thread::get_id() == _thread.get_id()); }
    auto protect() { return std::lock_guard(_lock); }
    auto queue_self_unless(bool ready) {
        return await_void(ready,
                          [this](Handle handle)
                          {
                              bool need_wakeup = false;
                              {
                                  auto guard = protect();
                                  need_wakeup = _queue.empty();
                                  _queue.push_back(handle);
                              }
                              if (need_wakeup) {
                                  _selector.wakeup();
                              }
                          });
    }
    auto enter_thread() { return queue_self_unless(is_my_thread()); }
    auto readable(int fd) {
        REQUIRE(is_my_thread());
        return await_void((fd < 0),
                          [this, fd](Handle handle)
                          {
                              auto [pos, ignore] = _state.try_emplace(fd, fd);
                              FdContext &state = pos->second;
                              REQUIRE(!state._reader && "conflicting reads detected");
                              state._reader = handle;
                              _check.insert(state._fd);
                          });
    }
    auto writable(int fd) {
        REQUIRE(is_my_thread());
        return await_void((fd < 0),
                          [this, fd](Handle handle)
                          {
                              auto [pos, ignore] = _state.try_emplace(fd, fd);
                              FdContext &state = pos->second;
                              REQUIRE(!state._writer && "conflicting write detected");
                              state._writer = handle;
                              _check.insert(state._fd);
                          });
    }
    void update_epoll_state() {
        for (int fd: _check) {
            auto pos = _state.find(fd);
            REQUIRE(pos != _state.end());
            FdContext &ctx = pos->second;
            const bool keep_entry = (ctx._reader || ctx._writer);
            const bool was_added = (ctx._epoll_read || ctx._epoll_write);
            if (keep_entry) {
                if (was_added) {
                    bool read_changed = ctx._epoll_read != bool(ctx._reader);
                    bool write_changed = ctx._epoll_write != bool(ctx._writer);
                    if (read_changed || write_changed) {
                        _selector.update(ctx._fd, ctx, bool(ctx._reader), bool(ctx._writer));
                    }
                } else {
                    _selector.add(ctx._fd, ctx, bool(ctx._reader), bool(ctx._writer));
                }
                ctx._epoll_read = bool(ctx._reader);
                ctx._epoll_write = bool(ctx._writer);
            } else {
                if (was_added) {
                    _selector.remove(ctx._fd);
                }
                _state.erase(pos);
            }
        }
        _check.clear();
    }
    void handle_wakeup() { _check_queue = true; }
    void handle_queue() {
        if (!_check_queue) {
            return;
        }
        _check_queue = false;
        {
            auto guard = protect();
            std::swap(_todo, _queue);
        }
        for (auto &&handle: _todo) {
            handle.resume();
        }
        _todo.clear();
    }
    void handle_event(FdContext &ctx, bool read, bool write) {
        _check.insert(ctx._fd);
        if (read && ctx._reader) {
            auto reader = std::exchange(ctx._reader, nullptr);
            reader.resume();
        }
        if (write && ctx._writer) {
            auto writer = std::exchange(ctx._writer, nullptr);
            writer.resume();
        }
    }
    vespalib::string get_impl_spec() override {
        return "selector-thread";
    }
    Lazy<SocketHandle> accept(ServerSocket &server_socket) override {
        co_await enter_thread();
        co_await readable(server_socket.get_fd());
        co_return server_socket.accept();
    }
    Lazy<SocketHandle> connect(const SocketAddress &addr) override {
        co_await enter_thread();
        auto tweak = [](SocketHandle &handle){ return handle.set_blocking(false); };
        auto socket = addr.connect(tweak);
        co_await writable(socket.get());
        co_return std::move(socket);
    }
    Lazy<ssize_t> read(SocketHandle &socket, char *buf, size_t len) override {
        co_await enter_thread();
        co_await readable(socket.get());
        co_return socket.read(buf, len);
    }
    Lazy<ssize_t> write(SocketHandle &socket, const char *buf, size_t len) override {
        co_await enter_thread();
        co_await writable(socket.get());
        co_return socket.write(buf, len);
    }
    Work schedule() override {
        co_await queue_self_unless(false);
        co_return Done{};
    }
    Detached shutdown() {
        co_await enter_thread();
        {
            auto guard = protect();
            _shutdown = true;
        }
    }
};

void
SelectorThread::main()
{
    const int ms_timeout = 100;
    while (!_shutdown) {
        update_epoll_state();
        _selector.poll(ms_timeout);
        _selector.dispatch(*this);
        handle_queue();
    }
}

SelectorThread::~SelectorThread() {
    shutdown();
    REQUIRE(!is_my_thread());
    _thread.join();
}

}

AsyncIo::~AsyncIo() = default;
AsyncIo::AsyncIo() = default;

std::shared_ptr<AsyncIo>
AsyncIo::create() {
    return std::make_shared<SelectorThread>();
}

}
