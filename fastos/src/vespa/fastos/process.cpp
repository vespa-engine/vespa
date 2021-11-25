// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "process.h"

FastOS_ProcessInterface::FastOS_ProcessInterface (const char *cmdLine,
                         bool pipeStdin,
                         FastOS_ProcessRedirectListener *stdoutListener,
                         FastOS_ProcessRedirectListener *stderrListener,
                         int bufferSize) :
    _cmdLine(cmdLine),
    _pipeStdin(pipeStdin),
    _stdoutListener(stdoutListener),
    _stderrListener(stderrListener),
    _bufferSize(bufferSize),
    _next(nullptr),
    _prev(nullptr)
{
}

FastOS_ProcessInterface::~FastOS_ProcessInterface () = default;
