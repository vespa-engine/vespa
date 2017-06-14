// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Class definition and implementation for FastOS_UNIX_Cond.
 *
 * @author  Div, Oivind H. Danielsen
 */

#pragma once

#include <vespa/fastos/cond.h>


class FastOS_UNIX_Cond : public FastOS_CondInterface
{
private:
    FastOS_UNIX_Cond(const FastOS_UNIX_Cond &);
    FastOS_UNIX_Cond& operator=(const FastOS_UNIX_Cond &);

    pthread_cond_t _cond;

public:
    FastOS_UNIX_Cond ();

    ~FastOS_UNIX_Cond();

    void Wait() override;

    bool TimedWait(int milliseconds) override;

    void Signal() override
    {
        pthread_cond_signal(&_cond);
    }

    void Broadcast() override
    {
        pthread_cond_broadcast(&_cond);
    }
};


