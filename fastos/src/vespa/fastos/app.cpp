// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Implementation of FastOS_ApplicationInterface methods.
 *
 * @author  Div, Oivind H. Danielsen
 */

#include "app.h"
#include "file.h"
#include "thread.h"
#include <cstring>
#include <fcntl.h>

FastOS_ApplicationInterface::FastOS_ApplicationInterface() :
    _argc(0),
    _argv(nullptr)
{
}

FastOS_ApplicationInterface::~FastOS_ApplicationInterface () = default;

bool FastOS_ApplicationInterface::Init ()
{
    bool rc=false;

    if (PreThreadInit()) {
        if (FastOS_Thread::InitializeClass()) {
            rc = true;
        } else {
            fprintf(stderr, "FastOS_Thread class initialization failed.\n");
        }
    } else
        fprintf(stderr, "FastOS_PreThreadInit failed.\n");

    return rc;
}


void FastOS_ApplicationInterface::Cleanup ()
{
    FastOS_Thread::CleanupClass();
}

int FastOS_ApplicationInterface::Entry (int argc, char **argv)
{
    int rc=255;

    _argc = argc;
    _argv = argv;

    if (Init()) {
        rc = Main();
    }

    Cleanup();

    return rc;
}
