// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
******************************************************************************
* @author  Oivind H. Danielsen
* @date    Creation date: 2000-02-02
* @file
* Class definition and implementation for FastOS_UNIX_Mutex
*****************************************************************************/



#pragma once


#include <vespa/fastos/mutex.h>


class FastOS_UNIX_Mutex : public FastOS_MutexInterface
{
private:
    FastOS_UNIX_Mutex(const FastOS_UNIX_Mutex &other);
    FastOS_UNIX_Mutex & operator = (const FastOS_UNIX_Mutex &other);
protected:
    pthread_mutex_t _mutex;

public:
    FastOS_UNIX_Mutex();

    ~FastOS_UNIX_Mutex();

    bool TryLock () override {
        return pthread_mutex_trylock(&_mutex) == 0;
    }

    void Lock() override {
        pthread_mutex_lock(&_mutex);
    }

    void Unlock() override {
        pthread_mutex_unlock(&_mutex);
    }
};


