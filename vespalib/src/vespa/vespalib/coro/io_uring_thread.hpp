// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// this file is included by async_io.cpp if VESPA_HAS_IO_URING is defined

#include <liburing.h>
#include <liburing/io_uring.h>
#include <sys/eventfd.h>

namespace vespalib::coro {
namespace {

using Handle = std::coroutine_handle<>;
using cqe_res_t = int32_t;

// Server sockets are always non-blocking. We need to temporarily set
// them to blocking mode to avoid that async accept return -EAGAIN.
struct BlockingGuard {
    int fd;
    BlockingGuard(int fd_in) : fd(fd_in) {
        SocketOptions::set_blocking(fd, true);
    }
    ~BlockingGuard() {
        SocketOptions::set_blocking(fd, false);
    }
};

struct UringProbe {
    io_uring_probe *probe;
    UringProbe() : probe(io_uring_get_probe()) {}
    ~UringProbe() { free(probe); }
    bool check(int opcode) {
        return probe && io_uring_opcode_supported(probe, opcode);
    }
    static bool check_support() {
        UringProbe probe;
        return probe.check(IORING_OP_ACCEPT)
            && probe.check(IORING_OP_CONNECT)
            && probe.check(IORING_OP_READ)
            && probe.check(IORING_OP_WRITE);
    }
};

struct Uring {
    io_uring uring;
    size_t pending;
    Uring() : pending(0) {
        int res = io_uring_queue_init(4096, &uring, 0);
        REQUIRE_EQ(res, 0);
    }
    auto *get_sqe() {
        auto *res = io_uring_get_sqe(&uring);
        while (res == nullptr) {
            auto submit_res = io_uring_submit(&uring);
            REQUIRE(submit_res >= 0);
            res = io_uring_get_sqe(&uring);
        }
        ++pending;
        return res;
    }
    void submit_and_dispatch() {
        auto res = io_uring_submit_and_wait(&uring, 1);
        REQUIRE(res >= 0);
        io_uring_cqe *cqe = nullptr;
        while (io_uring_peek_cqe(&uring, &cqe) == 0) {
            auto wf = WaitingFor<cqe_res_t>::from_pointer(io_uring_cqe_get_data(cqe));
            wf.set_value(cqe->res);
            io_uring_cqe_seen(&uring, cqe);
            --pending;
        }
    }
    void drain_pending() {
        while (pending > 0) {
            auto res = io_uring_submit_and_wait(&uring, 1);
            REQUIRE(res >= 0);
            io_uring_cqe *cqe = nullptr;
            while (io_uring_peek_cqe(&uring, &cqe) == 0) {
                auto wf = WaitingFor<cqe_res_t>::from_pointer(io_uring_cqe_get_data(cqe));
                wf.set_value(-ECANCELED);
                io_uring_cqe_seen(&uring, cqe);
                --pending;
            }
        }
    }
    ~Uring() {
        REQUIRE_EQ(pending, 0u);
        io_uring_queue_exit(&uring);
    }
};

auto wait_for_sqe(auto *sqe) {
    return wait_for<cqe_res_t>([sqe](auto wf)
                               {
                                   io_uring_sqe_set_data(sqe, wf.release());
                               });
}

struct IoUringThread : AsyncIo {

    struct RunGuard;
    using ThreadId = std::atomic<std::thread::id>;
    using RunOp = WaitingFor<bool>;
    
    Uring              _uring;
    SocketHandle       _event;
    std::thread        _thread;
    ThreadId           _thread_id;
    std::vector<RunOp> _todo;
    std::mutex         _lock;
    std::vector<RunOp> _queue;

    IoUringThread()
      : _uring(),
        _event(eventfd(0, 0)),
        _thread(),
        _thread_id(std::thread::id()),
        _todo(),
        _lock(),
        _queue()
    {
        static_assert(ThreadId::is_always_lock_free);
        REQUIRE(_event.valid());
    }
    void start() override;
    void main();
    void init_shutdown() override;
    void fini_shutdown() override;
    ~IoUringThread();
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
    void wakeup() {
        uint64_t value = 1;
        int res = ::write(_event.get(), &value, sizeof(value));
        REQUIRE_EQ(res, int(sizeof(value)));
    }
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
                                      wakeup();
                                  }
                                  return wf.nop();
                              });
    }
    void handle_queue(bool result) {
        {
            auto guard = protect();
            std::swap(_todo, _queue);
        }
        for (auto &&item: _todo) {
            auto wf = std::move(item);
            wf.set_value(result);
        }
        _todo.clear();
    }
    ImplTag get_impl_tag() override { return ImplTag::URING; }
    Lazy<SocketHandle> accept(ServerSocket &server_socket) override {
        int res = -ECANCELED;
        bool inside = in_thread() ? true : co_await async_run();
        if (inside) {
            BlockingGuard blocking_guard(server_socket.get_fd());
            auto *sqe = _uring.get_sqe();
            io_uring_prep_accept(sqe, server_socket.get_fd(), nullptr, nullptr, 0);
            res = co_await wait_for_sqe(sqe);
        }
        co_return SocketHandle(res);
    }
    Lazy<SocketHandle> connect(const SocketAddress &addr) override {
        bool inside = in_thread() ? true : co_await async_run();
        if (inside) {
            SocketHandle handle = addr.raw_socket();
            if (handle.valid()) {
                auto *sqe = _uring.get_sqe();
                io_uring_prep_connect(sqe, handle.get(), addr.raw_addr(), addr.raw_addr_len());
                auto res = co_await wait_for_sqe(sqe);
                if (res < 0) {
                    handle.reset(res);
                }
            }
            co_return handle;
        }
        co_return SocketHandle(-ECANCELED);
    }
    Lazy<ssize_t> read(SocketHandle &socket, char *buf, size_t len) override {
        ssize_t res = -ECANCELED;
        bool inside = in_thread() ? true : co_await async_run();
        if (inside) {
            auto *sqe = _uring.get_sqe();
            io_uring_prep_read(sqe, socket.get(), buf, len, 0);
            res = co_await wait_for_sqe(sqe);
        }
        co_return res;
    }
    Lazy<ssize_t> write(SocketHandle &socket, const char *buf, size_t len) override {
        ssize_t res = -ECANCELED;
        bool inside = in_thread() ? true : co_await async_run();
        if (inside) {
            auto *sqe = _uring.get_sqe();
            io_uring_prep_write(sqe, socket.get(), buf, len, 0);
            res = co_await wait_for_sqe(sqe);
        }
        co_return res;
    }
    Lazy<bool> schedule() override {
        co_return co_await async_run();
    }
    Detached consume_events() {
        uint64_t value = 0;
        REQUIRE(in_thread());
        int res = sizeof(value);
        while (running() && (res == sizeof(value))) {
            auto *sqe = _uring.get_sqe();
            io_uring_prep_read(sqe, _event.get(), &value, sizeof(value), 0);
            res = co_await wait_for_sqe(sqe);
        }
    }
    Detached async_shutdown() {
        bool inside = in_thread() ? true : co_await async_run();
        REQUIRE(inside && "unable to initialize shutdown of internal thread");
        {
            auto guard = protect();
            _thread_id = std::thread::id();
        }
    }
};

void
IoUringThread::start()
{
    _thread = std::thread(&IoUringThread::main, this);
    _thread_id.wait(std::thread::id());
}

struct IoUringThread::RunGuard {
    IoUringThread &self;
    RunGuard(IoUringThread &self_in) noexcept : self(self_in) {
        self._thread_id = std::this_thread::get_id();
        self._thread_id.notify_all();
        self.consume_events();
    }
    ~RunGuard() {
        REQUIRE(self.stopped());
        self.wakeup();
        self.handle_queue(false);
        self._uring.drain_pending();
    }
};

void
IoUringThread::main()
{
    RunGuard guard(*this);
    while (running()) {
        _uring.submit_and_dispatch();
        handle_queue(true);
    }
}

void
IoUringThread::init_shutdown()
{
    async_shutdown();
}

void
IoUringThread::fini_shutdown()
{
    _thread.join();
}

IoUringThread::~IoUringThread()
{
    REQUIRE(_todo.empty());
    REQUIRE(_queue.empty());
}

} // <unnamed>
} // vespalib::coro
