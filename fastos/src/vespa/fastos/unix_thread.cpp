// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/thread.h>
#include <atomic>
#include <thread>

namespace {
   std::atomic_size_t _G_nextCpuId(0);
   volatile size_t _G_maxNumCpus=0;  // Non zero means use cpu pinning.
}

bool FastOS_UNIX_Thread::InitializeClass ()
{
    if (getenv("VESPA_PIN_THREAD_TO_CORE") != NULL) {
        _G_maxNumCpus = std::thread::hardware_concurrency();
        fprintf(stderr, "Will pin threads to CPU. Using %ld cores\n", _G_maxNumCpus);
        if (getenv("VESPA_MAX_CORES") != NULL) {
            size_t maxCores = strtoul(getenv("VESPA_MAX_CORES"), NULL, 0);
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

    pthread_attr_setstacksize(&attr, stackSize);
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

    if (stackGuardSize != 0) {
        pthread_attr_setguardsize(&attr, stackGuardSize);
    }

    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

    rc = (0 == pthread_create(&_handle, &attr, FastOS_ThreadHook, this));
    if (rc)
        _handleValid = true;

    pthread_attr_destroy(&attr);

    return rc;
}

void FastOS_UNIX_Thread::PreEntry ()
{
}

FastOS_UNIX_Thread::~FastOS_UNIX_Thread(void)
{
    void *value;

    // Wait for thread library cleanup to complete.
    if (_handleValid) {
        value = NULL;
        pthread_join(_handle, &value);
    }
}

FastOS_ThreadId FastOS_UNIX_Thread::GetThreadId ()
{
    return _handle;
}

FastOS_ThreadId FastOS_UNIX_Thread::GetCurrentThreadId ()
{
    return pthread_self();
}

bool FastOS_UNIX_Thread::CompareThreadIds (FastOS_ThreadId a,
        FastOS_ThreadId b)
{
    return (pthread_equal(a, b) != 0);
}
