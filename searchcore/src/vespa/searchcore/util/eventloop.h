// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1999-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once


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


