// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
******************************************************************************
* @author  Oivind H. Danielsen
* @date    Creation date: 2000-02-02
* @file
* Class definition for FastOS_UNIX_Thread
*****************************************************************************/

#pragma once

#include <vespa/fastos/thread.h>

class FastOS_UNIX_Thread : public FastOS_ThreadInterface
{
private:
    FastOS_UNIX_Thread(const FastOS_UNIX_Thread &);
    FastOS_UNIX_Thread& operator=(const FastOS_UNIX_Thread &);

protected:
    pthread_t _handle;
    struct sched_param _normal_schedparam;
    int _normal_policy;
    bool _handleValid;
    bool _schedparams_ok;
    bool _schedparams_changed;

    bool Initialize (int stackSize, int stackGuardSize);
    void PreEntry ();

public:
    static bool InitializeClass ();
    static bool CleanupClass ();

    FastOS_UNIX_Thread(FastOS_ThreadPool *pool)
        : FastOS_ThreadInterface(pool),
          _handle(),
          _normal_schedparam(),
          _normal_policy(0),
          _handleValid(false),
          _schedparams_ok(false),
          _schedparams_changed(false)
    {}

    virtual ~FastOS_UNIX_Thread(void);

    static bool Sleep (int ms)
    {
        bool rc=false;

        if (ms > 0) {
            usleep(ms*1000);
            rc = true;
        }

        return rc;
    }

    bool SetPriority (const Priority priority);
    FastOS_ThreadId GetThreadId ();
    static bool CompareThreadIds (FastOS_ThreadId a,
                                  FastOS_ThreadId b);
    static FastOS_ThreadId GetCurrentThreadId ();
};


