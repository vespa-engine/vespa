// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "signalhandler.h"
#include "backtrace.h"
#ifdef __APPLE__
#define BOOST_STACKTRACE_GNU_SOURCE_NOT_REQUIRED
#endif
#include <boost/stacktrace/safe_dump_to.hpp> // Header-only dependency
#include <boost/stacktrace/frame.hpp>
#include <array>
#include <atomic>
#include <cassert>
#include <chrono>
#include <mutex>
#include <thread>
#include <typeinfo>

using namespace std::chrono_literals;

namespace vespalib {

std::vector<SignalHandler*> SignalHandler::_handlers;

namespace {

// 31 bit concurrency counter, 1 (lsb) bit indicating shutdown
std::atomic<int> signal_counter;

class Shutdown
{
public:
    ~Shutdown() {
        SignalHandler::shutdown();
    }
};

struct SharedBacktraceData {
    std::array<void*, 64> _stack_frames{};
    uint32_t              _n_dumped_frames{0};
    std::atomic<bool>     _want_backtrace{false};
    std::atomic<bool>     _signal_handler_done{false};
    bool                  _signal_is_hooked{false}; // Non-atomic; written at process init time before threads are started
};

// We make the assumption that no extra threads can exist (and be traced) prior to global ctors being invoked.
SharedBacktraceData _shared_backtrace_data;

}

SignalHandler SignalHandler::HUP(SIGHUP);
SignalHandler SignalHandler::INT(SIGINT);
SignalHandler SignalHandler::TERM(SIGTERM);
SignalHandler SignalHandler::CHLD(SIGCHLD);
SignalHandler SignalHandler::PIPE(SIGPIPE);
SignalHandler SignalHandler::SEGV(SIGSEGV);
SignalHandler SignalHandler::ABRT(SIGABRT);
SignalHandler SignalHandler::BUS(SIGBUS);
SignalHandler SignalHandler::ILL(SIGILL);
SignalHandler SignalHandler::TRAP(SIGTRAP);
SignalHandler SignalHandler::FPE(SIGFPE);
SignalHandler SignalHandler::QUIT(SIGQUIT);
SignalHandler SignalHandler::USR1(SIGUSR1);
SignalHandler SignalHandler::USR2(SIGUSR2);

// Clear SignalHandler::_handlers in a slightly less unsafe manner.
Shutdown shutdown;

void
SignalHandler::handleSignal(int signal) noexcept
{
    static_assert(std::atomic<int>::is_always_lock_free, "signal_counter must be lock free");
    if ((signal_counter.fetch_add(2) & 1) == 0) {
        if ((((size_t)signal) < _handlers.size()) && (_handlers[signal] != nullptr)) {
            _handlers[signal]->gotSignal();
        }
    }
    signal_counter.fetch_sub(2);
}

void
SignalHandler::gotSignal() noexcept
{
    if (_signal != SIGUSR2) {
        _gotSignal.store(1, std::memory_order_relaxed);
    } else {
        dump_current_thread_stack_to_shared_state();
    }
}

void
SignalHandler::dump_current_thread_stack_to_shared_state() noexcept
{
    // Best-effort attempt at making signal handler reentrancy-safe
    bool expected = true;
    if (!_shared_backtrace_data._want_backtrace.compare_exchange_strong(expected, false)) {
        return; // Someone else is already inside the house...!
    }
    auto& frames_buf = _shared_backtrace_data._stack_frames;
    static_assert(std::is_same_v<const void*, boost::stacktrace::frame::native_frame_ptr_t>);
    // Note: safe_dump_to() takes in buffer size in _bytes_, not in number of frames.
    const auto n_frames = boost::stacktrace::safe_dump_to(frames_buf.data(), frames_buf.size() * sizeof(void*));
    _shared_backtrace_data._n_dumped_frames = static_cast<uint32_t>(n_frames);
    _shared_backtrace_data._signal_handler_done.store(true);
}

SignalHandler::SignalHandler(int signal)
    : _signal(signal),
      _gotSignal(0)
{
    assert(signal >= 0);
    while (_handlers.size() < ((size_t)(signal + 1))) {
        _handlers.push_back(nullptr);
    }
    assert(_handlers[signal] == nullptr);
    _handlers[signal] = this;
}

void
SignalHandler::hook()
{
    struct sigaction act;
    act.sa_handler = handleSignal;
    sigemptyset(&act.sa_mask);
    act.sa_flags = 0;
    sigaction(_signal, &act, nullptr);
}

void
SignalHandler::ignore()
{
    struct sigaction act;
    act.sa_handler = SIG_IGN;
    sigemptyset(&act.sa_mask);
    act.sa_flags = 0;
    sigaction(_signal, &act, nullptr);
}

bool
SignalHandler::check() const
{
    return (_gotSignal.load(std::memory_order_relaxed) != 0);
}

void
SignalHandler::clear()
{
    _gotSignal.store(0, std::memory_order_relaxed);
}

void
SignalHandler::unhook()
{
    struct sigaction act;
    act.sa_handler = SIG_DFL;
    sigemptyset(&act.sa_mask);
    act.sa_flags = 0;
    sigaction(_signal, &act, nullptr);
}

void
SignalHandler::enable_cross_thread_stack_tracing()
{
    USR2.hook(); // Our secret sauce
    _shared_backtrace_data._signal_is_hooked = true;
}

/*
 * There's no existing API for conveniently getting the stack trace of any other thread
 * than your own, so we have to turn things around a bit and make the target thread
 * dump its own stack in a way that we can then safely access and return it.
 *
 * We allow for hooking SIGUSR2 at process startup time and use this as our dedicated
 * stack dump signal internally. The process of stack dumping is then basically:
 *
 *  1. Caller sends SIGUSR2 to the target thread. This happens within a global mutex
 *     that ensures no other callers can attempt to dump stack at the same time. This
 *     is because we use a single, globally shared state between caller and target
 *     (the same signal handler function is used by all threads).
 *  2. Caller acquire-polls (with 1ms sleep) for target thread signal handler completion.
 *     Fancy technologies such as mutexes and condition variables are not safe to use
 *     from within a signal handler and therefore cannot be used.
 *  3. Target thread suddenly finds itself in Narnia (signal handler). Since the
 *     signal is the magical SIGUSR2, it proceeds to dump its stack frame addresses in
 *     a shared buffer. It then toggles completion, with release semantics, before
 *     returning to its regularly scheduled programming.
 *  4. Caller exits poll-loop and assembles a complete stack trace from the frame
 *     addresses in the shared buffer, all demangled and shiny.
 */
string
SignalHandler::get_cross_thread_stack_trace(pthread_t thread_id)
{
    if (!_shared_backtrace_data._signal_is_hooked) {
        return "(cross-thread stack tracing is not enabled in process)";
    }
    // This will only work with pthreads, but then again, so will Vespa.
    if (thread_id == pthread_self()) {
        return vespalib::getStackTrace(1); // Skip this function's frame.
    }

    static std::mutex stack_dump_caller_mutex;
    std::lock_guard guard(stack_dump_caller_mutex);

    assert(!_shared_backtrace_data._want_backtrace.load());
    _shared_backtrace_data._want_backtrace.store(true);

    if (pthread_kill(thread_id, SIGUSR2) != 0) {
        _shared_backtrace_data._want_backtrace.store(false);
        return "(pthread_kill() failed; could not get backtrace)";
    }
    bool expected_done = true;
    while (!_shared_backtrace_data._signal_handler_done.compare_exchange_strong(expected_done, false)) {
        std::this_thread::sleep_for(1ms); // TODO yield instead?
        expected_done = true;
    }
    constexpr int frames_to_skip = 4; // handleSignal() -> gotSignal() -> dump_current_thread_...() -> backtrace()
    return vespalib::getStackTrace(frames_to_skip, _shared_backtrace_data._stack_frames.data(),
                                   static_cast<int>(_shared_backtrace_data._n_dumped_frames));
}

void
SignalHandler::shutdown()
{
    while ((signal_counter.fetch_or(1) & ~1) != 0) {
        std::this_thread::sleep_for(10ms);
    }
    for (auto* handler : _handlers) {
        if (handler != nullptr) {
            // Ignore SIGTERM at shutdown in case valgrind is used.
            if (handler->_signal == SIGTERM) {
                handler->ignore();
            } else {
                handler->unhook();
            }
        }
    }
    std::vector<SignalHandler *>().swap(_handlers);
}

} // vespalib
