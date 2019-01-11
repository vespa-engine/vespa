// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sigbushandler.h"
#include "statefile.h"
#include "statebuf.h"
#include <unistd.h>
#include <cstring>

namespace search {

SigBusHandler *SigBusHandler::_instance = nullptr;

namespace {

std::atomic<int> sigBusNesting;

class TryLockGuard
{
    bool _gotLock;
public:
    TryLockGuard() noexcept
        : _gotLock(false)
    {
        int expzero = 0;
        _gotLock = sigBusNesting.compare_exchange_strong(expzero, 1);
    }

    ~TryLockGuard() noexcept {
        if (_gotLock) {
            sigBusNesting = 0;
        }
    }

    bool gotLock() const noexcept { return _gotLock; }
};


/*
 * Write string to standard error using only async signal safe methods.
 */
void
mystderr(const char *msg) noexcept
{
    const char *p = msg;
    while (*p != '\0') {
        ++p;
    }
    write(STDERR_FILENO, msg, static_cast<size_t>(p - msg));
}

}

void
SigBusHandler::trap()
{
    struct sigaction sa;
    _instance = this;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = SigBusHandler::forward;
    sa.sa_flags = SA_SIGINFO;
    sigemptyset(&sa.sa_mask);
    sigaddset(&sa.sa_mask, SIGBUS);
    sigaction(SIGBUS, &sa, nullptr);
    _trapped = true;
}


void
SigBusHandler::untrap()
{
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = SIG_DFL;
    sa.sa_flags = 0;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGBUS, &sa, nullptr);
    _trapped = false;
    _instance = nullptr;
}


void
SigBusHandler::forward(int sig, siginfo_t *si, void *ucv)
{
    _instance->handle(sig, si, ucv);
}


void
SigBusHandler::handle(int sig, siginfo_t *si, void *ucv)
{
    (void) sig;
    (void) ucv;

    StateBuf sb(_buf, sizeof(_buf));
    bool raced = false;
    do {
        // Protect against multiple threads.
        TryLockGuard guard;
        if (!guard.gotLock() || _fired) {
            raced = true;
            break;
        }
        sb.appendKey("state") << "down";
        sb.appendTimestamp();
        sb.appendKey("operation") << "sigbus";
        sb.appendKey("errno") << static_cast<long>(si->si_errno);
        sb.appendKey("code") << static_cast<long>(si->si_code);
        if (si->si_code != 0) {
            sb.appendAddr(si->si_addr);
        }
        sb << '\n';
        // TODO: Report backing store file, for quick diagnostics.
        if (_stateFile != nullptr) {
            _stateFile->addState(sb.base(), sb.size(), true);
        }
        _fired = true;
    } while (0);
    if (raced) {
        mystderr("SIGBUS handler call race, ignoring signal\n");
        sleep(5);
        return;
    }

    if (_unwind != nullptr) {
        // Unit test is using siglongjmp based unwinding
        sigjmp_buf *unwind = _unwind;
        _unwind = nullptr;
        untrap(); // Further bus errors will trigger core dump
        siglongjmp(*unwind, 1);
    } else {
        // Normal case, sleep 3 seconds (i.e. allow main thread to detect
        // issue and notify cluster controller) before returning and
        // likely core dumping.
        sleep(3);
        untrap(); // Further bus errors will trigger core dump
    }
}


SigBusHandler::SigBusHandler(StateFile *stateFile)
    : _stateFile(stateFile),
      _unwind(nullptr),
      _trapped(false),
      _fired(false)
{
    trap();
}

SigBusHandler::~SigBusHandler()
{
    untrap();
}

}
