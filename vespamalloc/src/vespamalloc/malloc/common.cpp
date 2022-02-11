// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "common.h"
#include <vespamalloc/util/callstack.h>
#include <pthread.h>

namespace vespamalloc {

std::atomic<uint32_t> Mutex::_threadCount(0);
bool     Mutex::_stopRecursion = true;

void Mutex::lock()
{
    if (_use) {
        pthread_mutex_lock(&_mutex);
    }
}
void Mutex::unlock()
{
    if (_use) {
        pthread_mutex_unlock(&_mutex);
    }
}

void Mutex::quit()
{
    if (_use) {
        _use = false;
        pthread_mutex_destroy(&_mutex);
    }
}

void Mutex::init() {
    if (!_use && ! _stopRecursion) {
        pthread_mutex_init(&_mutex, nullptr);
        _use = true;
    }
}

Guard::Guard(Mutex & m) :
     _mutex(&m)
{
    MallocRecurseOnSuspend(false);
    _mutex->lock();
    MallocRecurseOnSuspend(true);
}

FILE *  _G_logFile = stderr;
size_t  _G_bigBlockLimit = 0x80000000;

void
logStackTrace() {
    StackEntry st[32];
    size_t count = StackEntry::fillStack(st, NELEMS(st));
    st[4].info(_G_logFile);
    fprintf(_G_logFile, "\n");
    for(size_t i=1; (i < count) && (i < NELEMS(st)); i++) {
        const auto & s = st[i];
        if (s.valid()) {
            s.info(_G_logFile);
            fprintf(_G_logFile, " from ");
        }
    }
    fprintf(_G_logFile, "\n");
}

void
logBigBlock(const void *ptr, size_t exact, size_t adjusted, size_t gross)
{
    size_t sz(exact);
    if (std::max(std::max(sz, adjusted), gross) > _G_bigBlockLimit) {
        fprintf(_G_logFile, "validating %p(%ld, %ld, %ld) ", ptr, sz, adjusted, gross);
        logStackTrace();
    }
}

}

extern "C" void MallocRecurseOnSuspend(bool recurse)
{
    (void) recurse;
}
