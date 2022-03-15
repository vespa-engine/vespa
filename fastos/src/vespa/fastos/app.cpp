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
#ifdef __linux__
    char * fadvise = getenv("VESPA_FADVISE_OPTIONS");
    if (fadvise != nullptr) {
        int fadviseOptions(0);
        if (strstr(fadvise, "SEQUENTIAL")) { fadviseOptions |= POSIX_FADV_SEQUENTIAL; }
        if (strstr(fadvise, "RANDOM"))     { fadviseOptions |= POSIX_FADV_RANDOM; }
        if (strstr(fadvise, "WILLNEED"))   { fadviseOptions |= POSIX_FADV_WILLNEED; }
        if (strstr(fadvise, "DONTNEED"))   { fadviseOptions |= POSIX_FADV_DONTNEED; }
        if (strstr(fadvise, "NOREUSE"))    { fadviseOptions |= POSIX_FADV_NOREUSE; }
        FastOS_FileInterface::setDefaultFAdviseOptions(fadviseOptions);
    }
#endif
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
