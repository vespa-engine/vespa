// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "async_io.h"
#include "detached.h"
#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/config.h>

#include <thread>
#include <atomic>
#include <vector>
#include <map>
#include <set>

#ifdef VESPA_HAS_IO_URING
#include "io_uring_thread.hpp"
namespace {
bool can_use_io_uring() { return vespalib::coro::UringProbe::check_support(); }
vespalib::coro::AsyncIo::SP create_io_uring_thread() { return std::make_shared<vespalib::coro::IoUringThread>(); }
}
#else
namespace {
bool can_use_io_uring() { return false; }
vespalib::coro::AsyncIo::SP create_io_uring_thread() { abort(); }
}
#endif

namespace vespalib::coro {

namespace {

struct SelectorThread : AsyncIo {

    using BoolOp = WaitingFor<bool>;

    struct FdContext {
        int    _fd;
        bool   _epoll_read;
        bool   _epoll_write;
        BoolOp _reader;
        BoolOp _writer;
        FdContext(int fd_in)
          : _fd(fd_in),
            _epoll_read(false), _epoll_write(false),
            _reader(), _writer() {}
    };
    struct RunGuard;
    using ThreadId = std::atomic<std::thread::id>;

    std::map<int,FdContext> _state;
    std::set<int>           _check;
    Selector<FdContext>     _selector;
    std::thread             _thread;
    ThreadId                _thread_id;
    bool                    _check_queue;
    std::vector<BoolOp>     _todo;
    std::mutex              _lock;
    std::vector<BoolOp>     _queue;

    SelectorThread()
      : _state(),
        _check(),
        _selector(),
        _thread(),
        _thread_id(std::thread::id()),
        _check_queue(false),
        _todo(),
        _lock(),
        _queue()
    {
        static_assert(ThreadId::is_always_lock_free);
    }
    void start() override;
    void main();
    void init_shutdown() override;
    void fini_shutdown() override;
    ~SelectorThread();
    bool running() const noexcept {
        return (_thread_id.load(std::memory_order_relaxed) != std::thread::id());
    }
    bool stopped() const noexcept {
        return (_thread_id.load(std::memory_order_relaxed) == std::thread::id());
    }
    bool in_thread() const noexcept {
        return (std::this_thread::get_id() == _thread_id.load(std::memory_order_relaxed));
    }
    auto protect() { return std::lock_guard(_lock); }
    auto async_run() {
        return wait_for<bool>([this](auto wf)
                              {
                                  AsyncIo::SP wakeup_guard;
                                  {
                                      auto guard = protect();
                                      if (stopped()) {
                                          wf.set_value(false);
                                          return wf.mu();
                                      }
                                      if (_queue.empty()) {
                                          wakeup_guard = shared_from_this();
                                      }
                                      _queue.push_back(std::move(wf));
                                  }
                                  if (wakeup_guard) {
                                      _selector.wakeup();
                                  }
                                  return wf.nop();
                              });
    }
    auto readable(int fd) {
        return wait_for<bool>([fd, this](auto wf)
                              {
                                  if ((fd < 0) || stopped()) {
                                      wf.set_value(false);
                                      return wf.mu();
                                  }
                                  auto [pos, ignore] = _state.try_emplace(fd, fd);
                                  FdContext &state = pos->second;
                                  REQUIRE(!state._reader && "conflicting reads detected");
                                  state._reader = std::move(wf);
                                  _check.insert(state._fd);
                                  return wf.nop();
                              });
    }
    auto writable(int fd) {
        return wait_for<bool>([fd,this](auto wf)
                              {
                                  if ((fd < 0) || stopped()) {
                                      wf.set_value(false);
                                      return wf.mu();
                                  }
                                  auto [pos, ignore] = _state.try_emplace(fd, fd);
                                  FdContext &state = pos->second;
                                  REQUIRE(!state._writer && "conflicting writes detected");
                                  state._writer = std::move(wf);
                                  _check.insert(state._fd);
                                  return wf.nop();
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
    void handle_queue(bool result) {
        if (!_check_queue) {
            return;
        }
        _check_queue = false;
        {
            auto guard = protect();
            std::swap(_todo, _queue);
        }
        for (auto &&entry: _todo) {
            auto wf = std::move(entry);
            wf.set_value(result);
        }
        _todo.clear();
    }
    void handle_event(FdContext &ctx, bool read, bool write) {
        _check.insert(ctx._fd);
        if (read && ctx._reader) {
            auto reader = std::move(ctx._reader);
            reader.set_value(true);
        }
        if (write && ctx._writer) {
            auto writer = std::move(ctx._writer);
            writer.set_value(true);
        }
    }
    ImplTag get_impl_tag() override { return ImplTag::EPOLL; }
    Lazy<SocketHandle> accept(ServerSocket &server_socket) override {
        bool inside = in_thread() ? true : co_await async_run();
        if (inside) {
            bool can_read = co_await readable(server_socket.get_fd());
            if (can_read) {
                auto res = server_socket.accept();
                if (res.valid()) {
                    res.set_blocking(false);
                }
                co_return res;
            }
        }
        co_return SocketHandle(-ECANCELED);
    }
    Lazy<SocketHandle> connect(const SocketAddress &addr) override {
        bool inside = in_thread() ? true : co_await async_run();
        if (inside) {
            auto tweak = [](SocketHandle &handle){ return handle.set_blocking(false); };
            auto socket = addr.connect(tweak);
            bool can_write = co_await writable(socket.get());
            if (can_write) {
                co_return socket;
            }
        }
        co_return SocketHandle(-ECANCELED);
    }
    Lazy<ssize_t> read(SocketHandle &socket, char *buf, size_t len) override {
        bool inside = in_thread() ? true : co_await async_run();
        if (inside) {
            bool can_read = co_await readable(socket.get());
            if (can_read) {
                ssize_t res = socket.read(buf, len);
                co_return (res < 0) ? -errno : res;
            }
        }
        co_return -ECANCELED;
    }
    Lazy<ssize_t> write(SocketHandle &socket, const char *buf, size_t len) override {
        bool inside = in_thread() ? true : co_await async_run();
        if (inside) {
            bool can_write = co_await writable(socket.get());
            if (can_write) {
                ssize_t res = socket.write(buf, len);
                co_return (res < 0) ? -errno : res;
            }
        }
        co_return -ECANCELED;
    }
    Lazy<bool> schedule() override {
        co_return co_await async_run();
    }
    Detached async_shutdown() {
        bool inside = co_await async_run();
        REQUIRE(inside && "unable to initialize shutdown of internal thread");
        {
            auto guard = protect();
            _thread_id = std::thread::id();
        }
    }
};

void
SelectorThread::start()
{
    _thread = std::thread(&SelectorThread::main, this);
    _thread_id.wait(std::thread::id());
}

struct SelectorThread::RunGuard {
    SelectorThread &self;
    RunGuard(SelectorThread &self_in) noexcept : self(self_in) {
        self._thread_id = std::this_thread::get_id();
        self._thread_id.notify_all();
    }
    ~RunGuard() {
        REQUIRE(self.stopped());
        self._check.clear();
        for (auto &entry: self._state) {
            FdContext &ctx = entry.second;
            const bool was_added = (ctx._epoll_read || ctx._epoll_write);
            if (was_added) {
                self._selector.remove(ctx._fd);
            }
            if (ctx._reader) {
                auto reader = std::move(ctx._reader);
                reader.set_value(false);
            }
            if (ctx._writer) {
                auto writer = std::move(ctx._writer);
                writer.set_value(false);
            }
        }
        self._state.clear();
        REQUIRE(self._check.empty());
        self._check_queue = true;
        self.handle_queue(false);
    }
};

void
SelectorThread::main()
{
    RunGuard guard(*this);
    while (running()) {
        update_epoll_state();
        _selector.poll(1000);
        _selector.dispatch(*this);
        handle_queue(true);
    }
}

void
SelectorThread::init_shutdown()
{
    async_shutdown();
}

void
SelectorThread::fini_shutdown()
{
    _thread.join();
}

SelectorThread::~SelectorThread()
{
    REQUIRE(_state.empty());
    REQUIRE(_check.empty());
    REQUIRE(_todo.empty());
    REQUIRE(_queue.empty());
}

} // <unnamed>

AsyncIo::~AsyncIo() = default;
AsyncIo::AsyncIo() = default;

AsyncIo::Owner::Owner(std::shared_ptr<AsyncIo> async_io)
  : _async_io(std::move(async_io)),
    _init_shutdown_called(false),
    _fini_shutdown_called(false)
{
    _async_io->start();
}

void
AsyncIo::Owner::init_shutdown()
{
    if (!_init_shutdown_called) {
        if (_async_io) {
            _async_io->init_shutdown();
        }
        _init_shutdown_called = true;
    }
}

void
AsyncIo::Owner::fini_shutdown()
{
    if (!_fini_shutdown_called) {
        init_shutdown();
        if (_async_io) {
            _async_io->fini_shutdown();
        }
        _fini_shutdown_called = true;
    }
}

AsyncIo::Owner::~Owner()
{
    fini_shutdown();
}

AsyncIo::Owner
AsyncIo::create(ImplTag prefer_impl) {
    if (prefer_impl == ImplTag::URING && can_use_io_uring()) {
        return Owner(create_io_uring_thread());
    }
    return Owner(std::make_shared<SelectorThread>());
}

} // vespalib::coro
