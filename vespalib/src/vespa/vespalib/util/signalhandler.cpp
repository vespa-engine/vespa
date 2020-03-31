// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "signalhandler.h"
#include <cassert>

namespace vespalib {

std::vector<SignalHandler*> SignalHandler::_handlers;

namespace {

class Shutdown
{
public:
    ~Shutdown() {
        SignalHandler::shutdown();
    }
};

}

// Clear SignalHandler::_handlers in a slightly less unsafe manner.
Shutdown shutdown;

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

void
SignalHandler::handleSignal(int signal)
{
    if ((((size_t)signal) < _handlers.size()) && (_handlers[signal] != 0)) {
        _handlers[signal]->gotSignal();
    }
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
    // Block SIGTERM at shutdown in case valgrind is used.
    sigset_t signals_to_block;
    sigemptyset(&signals_to_block);
    sigaddset(&signals_to_block, SIGTERM);
    sigprocmask(SIG_BLOCK, &signals_to_block, nullptr);
    for (std::vector<SignalHandler*>::iterator
             it = _handlers.begin(), ite = _handlers.end();
         it != ite;
         ++it) {
        if (*it != nullptr)
            (*it)->unhook();
    }
    std::vector<SignalHandler *>().swap(_handlers);
}

} // vespalib
