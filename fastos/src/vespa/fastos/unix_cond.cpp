// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/cond.h>

FastOS_UNIX_Cond::FastOS_UNIX_Cond(void)
    : FastOS_CondInterface(),
      _cond()
{
    pthread_cond_init(&_cond, NULL);
}

FastOS_UNIX_Cond::~FastOS_UNIX_Cond(void)
{
    pthread_cond_destroy(&_cond);
}

void
FastOS_UNIX_Cond::Wait(void)
{
    pthread_cond_wait(&_cond, &_mutex);
}

bool
FastOS_UNIX_Cond::TimedWait(int milliseconds)
{
    struct timeval currentTime;
    struct timespec absTime;
    int error;

    gettimeofday(&currentTime, NULL);

    int64_t ns = (static_cast<int64_t>(currentTime.tv_sec) *
                  static_cast<int64_t>(1000 * 1000 * 1000) +
                  static_cast<int64_t>(currentTime.tv_usec) *
                  static_cast<int64_t>(1000) +
                  static_cast<int64_t>(milliseconds) *
                  static_cast<int64_t>(1000 * 1000));

    absTime.tv_sec = static_cast<int>
                     (ns / static_cast<int64_t>(1000 * 1000 * 1000));
    absTime.tv_nsec = static_cast<int>
                      (ns % static_cast<int64_t>(1000 * 1000 * 1000));

    error = pthread_cond_timedwait(&_cond, &_mutex, &absTime);
    return error == 0;
}
