// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "common.h"
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
        pthread_mutex_init(&_mutex, NULL);
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


}

extern "C" void MallocRecurseOnSuspend(bool recurse)
{
    (void) recurse;
}
