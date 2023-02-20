// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "thread.h"

bool FastOS_UNIX_Thread::Initialize ()
{
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    _handleValid = (0 == pthread_create(&_handle, &attr, FastOS_ThreadHook, this));
    pthread_attr_destroy(&attr);

    return _handleValid;
}

FastOS_UNIX_Thread::~FastOS_UNIX_Thread()
{
    if (!_handleValid) return;

    void *value = nullptr;
    pthread_join(_handle, &value);
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
