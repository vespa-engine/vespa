// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "signalhandler.h"
#include <cassert>
#include <atomic>
#include <chrono>
#include <thread>

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

// Clear SignalHandler::_handlers in a slightly less unsafe manner.
Shutdown shutdown;

void
SignalHandler::handleSignal(int signal)
{
    static_assert(std::atomic<int>::is_always_lock_free, "signal_counter must be lock free");
    if ((signal_counter.fetch_add(2) & 1) == 0) {
        if ((((size_t)signal) < _handlers.size()) && (_handlers[signal] != 0)) {
            _handlers[signal]->gotSignal();
        }
    }
    signal_counter.fetch_sub(2);
}

void
SignalHandler::gotSignal()
{
    _gotSignal = 1;
}

SignalHandler::SignalHandler(int signal)
    : _signal(signal),
      _gotSignal(0)
{
    assert(signal >= 0);
    while (_handlers.size() < ((size_t)(signal + 1))) {
        _handlers.push_back(0);
    }
    assert(_handlers[signal] == 0);
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
    return (_gotSignal != 0);
}

void
SignalHandler::clear()
{
    _gotSignal = 0;
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
SignalHandler::shutdown()
{
    while ((signal_counter.fetch_or(1) & ~1) != 0) {
        std::this_thread::sleep_for(10ms);
    }
    for (std::vector<SignalHandler*>::iterator
             it = _handlers.begin(), ite = _handlers.end();
         it != ite;
         ++it) {
        if (*it != nullptr) {
            // Ignore SIGTERM at shutdown in case valgrind is used.
            if ((*it)->_signal == SIGTERM) {
                (*it)->ignore();
            } else {
                (*it)->unhook();
            }
        }
    }
    std::vector<SignalHandler *>().swap(_handlers);
}

} // vespalib
