// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "process.h"

FastOS_ProcessInterface::FastOS_ProcessInterface (const char *cmdLine,
                         FastOS_ProcessRedirectListener *stdoutListener,
                         FastOS_ProcessRedirectListener *stderrListener) :
    _cmdLine(cmdLine),
    _stdoutListener(stdoutListener),
    _stderrListener(stderrListener),
    _next(nullptr),
    _prev(nullptr)
{
}

FastOS_ProcessInterface::~FastOS_ProcessInterface () = default;
