// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "thread.h"
#include <atomic>
#include <thread>
#include <unistd.h>
#include <limits.h>

#ifdef __linux__
extern "C" { size_t __pthread_get_minstack(const pthread_attr_t *); }
#endif

namespace {
#ifdef __linux__
   std::atomic_size_t _G_nextCpuId(0);
#endif
   volatile size_t _G_maxNumCpus=0;  // Non zero means use cpu pinning.
}

bool FastOS_UNIX_Thread::InitializeClass ()
{
    if (getenv("VESPA_PIN_THREAD_TO_CORE") != nullptr) {
        _G_maxNumCpus = std::thread::hardware_concurrency();
        fprintf(stderr, "Will pin threads to CPU. Using %ld cores\n", _G_maxNumCpus);
        if (getenv("VESPA_MAX_CORES") != nullptr) {
            size_t maxCores = strtoul(getenv("VESPA_MAX_CORES"), nullptr, 0);
            fprintf(stderr, "Will limit to %ld", maxCores);
            if (maxCores < _G_maxNumCpus) {
                _G_maxNumCpus = maxCores;
            }
        }
    }
    return true;
}

bool FastOS_UNIX_Thread::CleanupClass ()
{
    return true;
}

bool FastOS_UNIX_Thread::Initialize (int stackSize, int stackGuardSize)
{
    bool rc=false;

    pthread_attr_t attr;
    pthread_attr_init(&attr);

    pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
#ifdef __linux__
    if (_G_maxNumCpus > 0) {
        int cpuid = _G_nextCpuId.fetch_add(1)%_G_maxNumCpus;
        cpu_set_t cpuset;
        CPU_ZERO(&cpuset);
        CPU_SET(cpuid, &cpuset);
        int retval = pthread_attr_setaffinity_np(&attr, sizeof(cpuset), &cpuset);
        if (retval != 0) {
            fprintf(stderr, "Pinning FAILURE retval = %d, errno=%d sizeof(cpuset_t)=%ld cpuid(%d)\n", retval, errno, sizeof(cpuset), cpuid);
        }
    }
#endif

    if (stackGuardSize != 0) {
        pthread_attr_setguardsize(&attr, stackGuardSize);
    }

    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

    size_t adjusted_stack_size = stackSize;
#ifdef __linux__
    ssize_t stack_needed_by_system = __pthread_get_minstack(&attr);
    adjusted_stack_size += stack_needed_by_system;
#else
    adjusted_stack_size += PTHREAD_STACK_MIN;
#endif
    if (getenv("VESPA_IGNORE_REQUESTED_STACK_SIZES") == nullptr) {
        //fprintf(stderr, "pthread_create: using adjusted stack size %zd\n", adjusted_stack_size);
        pthread_attr_setstacksize(&attr, adjusted_stack_size);
    } else {
        //fprintf(stderr, "pthread_create: ignoring requested stack size %d\n", stackSize);
    }

    rc = (0 == pthread_create(&_handle, &attr, FastOS_ThreadHook, this));
    if (rc)
        _handleValid = true;

    pthread_attr_destroy(&attr);

    return rc;
}

void FastOS_UNIX_Thread::PreEntry ()
{
}

FastOS_UNIX_Thread::~FastOS_UNIX_Thread()
{
    void *value;

    // Wait for thread library cleanup to complete.
    if (_handleValid) {
        value = nullptr;
        pthread_join(_handle, &value);
    }
}

FastOS_ThreadId FastOS_UNIX_Thread::GetThreadId () const noexcept
{
    return _handle;
}

FastOS_ThreadId FastOS_UNIX_Thread::GetCurrentThreadId ()
{
    return pthread_self();
}

bool FastOS_UNIX_Thread::CompareThreadIds (FastOS_ThreadId a, FastOS_ThreadId b)
{
    return (pthread_equal(a, b) != 0);
}
