// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
******************************************************************************
* @author  Oivind H. Danielsen
* @date    Creation date: 2000-01-18
* @file
* Implementation of FastOS_UNIX_Application methods.
*****************************************************************************/

#include "app.h"
#include "time.h"
#include <unistd.h>
#include <csignal>
#include <getopt.h>


FastOS_UNIX_Application::FastOS_UNIX_Application() = default;
FastOS_UNIX_Application::~FastOS_UNIX_Application() = default;

extern "C"
{
extern char **environ;
};

bool FastOS_UNIX_Application::PreThreadInit ()
{
    bool rc = true;
    if (FastOS_ApplicationInterface::PreThreadInit()) {
        // Ignore SIGPIPE
        struct sigaction act;
        act.sa_handler = SIG_IGN;
        sigemptyset(&act.sa_mask);
        act.sa_flags = 0;
        sigaction(SIGPIPE, &act, nullptr);
    } else {
        rc = false;
        fprintf(stderr, "FastOS_ApplicationInterface::PreThreadInit failed\n");
    }
    return rc;
}

bool FastOS_UNIX_Application::Init ()
{
    return FastOS_ApplicationInterface::Init();
}

void FastOS_UNIX_Application::Cleanup ()
{
    FastOS_ApplicationInterface::Cleanup();
}
