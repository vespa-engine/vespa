// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/time.h>

double
FastOS_TimeInterface::MicroSecsToNow(void) const
{
    FastOS_Time now;
    now.SetNow();
    return now.MicroSecs() - MicroSecs();
}

double
FastOS_TimeInterface::MilliSecsToNow(void) const
{
    FastOS_Time now;
    now.SetNow();
    return now.MilliSecs() - MilliSecs();
}
