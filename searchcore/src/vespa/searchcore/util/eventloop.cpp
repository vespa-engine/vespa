// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "eventloop.h"
#include <cstdio>

double FastS_TimeOut::_val[FastS_TimeOut::valCnt];

void
FastS_TimeOut::WriteTime(char* buffer, size_t bufsize, double xtime)
{
    snprintf(buffer, bufsize, "%.3fs ", xtime);
}
