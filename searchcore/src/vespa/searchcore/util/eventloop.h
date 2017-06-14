// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

class FastS_TimeOut
{
public:
    enum ValName {
        maxSockSilent,		// 0
        valCnt			// 1 - Must be last, used as array size:
    };
    static double _val[valCnt];

    static void WriteTime(char* buffer, size_t bufsize, double xtime);
};


