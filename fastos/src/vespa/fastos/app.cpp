// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Implementation of FastOS_ApplicationInterface methods.
 *
 * @author  Div, Oivind H. Danielsen
 */

#include "app.h"
#include "file.h"
#include "process.h"
#include "thread.h"
#include <cstring>
#include <fcntl.h>

FastOS_ApplicationInterface *FastOS_ProcessInterface::_app = nullptr;

FastOS_ThreadPool *FastOS_ApplicationInterface::GetThreadPool ()
{
    return _threadPool;
}

FastOS_ApplicationInterface::FastOS_ApplicationInterface() :
    _threadPool(nullptr),
    _processList(nullptr),
    _processListMutex(nullptr),
    _argc(0),
    _argv(nullptr)
{
    FastOS_ProcessInterface::_app = this;
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
            if (FastOS_File::InitializeClass()) {
                _processListMutex = new std::mutex;
                _threadPool = new FastOS_ThreadPool(128 * 1024);
                rc = true;
            } else
                fprintf(stderr, "FastOS_File class initialization failed.\n");
        } else
            fprintf(stderr, "FastOS_Thread class initialization failed.\n");
    } else
        fprintf(stderr, "FastOS_PreThreadInit failed.\n");

    return rc;
}


void FastOS_ApplicationInterface::Cleanup ()
{
    if(_threadPool != nullptr) {
        _threadPool->Close();
        delete _threadPool;
        _threadPool = nullptr;
    }

    if(_processListMutex != nullptr) {
        delete _processListMutex;
        _processListMutex = nullptr;
    }

    FastOS_File::CleanupClass();
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

void
FastOS_ApplicationInterface::AddChildProcess (FastOS_ProcessInterface *node)
{
    node->_prev = nullptr;
    node->_next = _processList;

    if(_processList != nullptr)
        _processList->_prev = node;

    _processList = node;
}

void
FastOS_ApplicationInterface::RemoveChildProcess (FastOS_ProcessInterface *node)
{
    if(node->_prev)
        node->_prev->_next = node->_next;
    else
        _processList = node->_next;

    if(node->_next)
    {
        node->_next->_prev = node->_prev;
        node->_next = nullptr;
    }

    if(node->_prev != nullptr)
        node->_prev = nullptr;
}

bool
FastOS_ApplicationInterface::useProcessStarter() const
{
    return false;
}
bool
FastOS_ApplicationInterface::useIPCHelper() const
{
    return useProcessStarter();
}
