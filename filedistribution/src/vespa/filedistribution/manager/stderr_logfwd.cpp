// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/filedistribution/common/logfwd.h>

#include <iostream>
#include <vector>


void filedistribution::logfwd::log_forward(LogLevel level, const char* file, int line, const char* fmt, ...)
{
    if (level == debug || level == info)
        return;

    const size_t maxSize(0x8000);
    std::vector<char> payload(maxSize);
    char * buf = &payload[0];

    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, maxSize, fmt, args);
    va_end(args);

    std::cerr <<"Error: " << buf <<" File: " <<file <<" Line: " <<line <<std::endl;
}
