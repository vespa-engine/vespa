// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Implementation of FastOS_ApplicationInterface methods.
 *
 * @author  Div, Oivind H. Danielsen
 */

#include <vespa/fastos/app.h>
#include <vespa/fastos/socket.h>
#include <vespa/fastos/file.h>

#include <vespa/fastos/process.h>
#include <vespa/fastos/thread.h>

FastOS_ApplicationInterface *FastOS_ProcessInterface::_app;
FastOS_ThreadPool *FastOS_ProcessInterface::GetThreadPool ()
{
    return _app->GetThreadPool();
}

FastOS_ThreadPool *FastOS_ApplicationInterface::GetThreadPool ()
{
    return _threadPool;
}

FastOS_ApplicationInterface::FastOS_ApplicationInterface() :
    _threadPool(NULL),
    _processList(NULL),
    _processListMutex(NULL),
    _disableLeakReporting(false),
    _argc(0),
    _argv(NULL)
{
    FastOS_ProcessInterface::_app = this;
    char * fadvise = getenv("VESPA_FADVISE_OPTIONS");
    if (fadvise != NULL) {
        int fadviseOptions(0);
        if (strstr(fadvise, "SEQUENTIAL")) { fadviseOptions |= POSIX_FADV_SEQUENTIAL; }
        if (strstr(fadvise, "RANDOM"))     { fadviseOptions |= POSIX_FADV_RANDOM; }
        if (strstr(fadvise, "WILLNEED"))   { fadviseOptions |= POSIX_FADV_WILLNEED; }
        if (strstr(fadvise, "DONTNEED"))   { fadviseOptions |= POSIX_FADV_DONTNEED; }
        if (strstr(fadvise, "NOREUSE"))    { fadviseOptions |= POSIX_FADV_NOREUSE; }
        FastOS_FileInterface::setDefaultFAdviseOptions(fadviseOptions);
    }
}

FastOS_ApplicationInterface::~FastOS_ApplicationInterface ()
{
}

bool FastOS_ApplicationInterface::Init ()
{
    bool rc=false;

    if(PreThreadInit())
    {
        if(FastOS_Thread::InitializeClass())
        {
            if(FastOS_File::InitializeClass())
            {
                const char *errorMsg = FastOS_Socket::InitializeServices();

                if(errorMsg == NULL)
                {
                    _processListMutex = new FastOS_Mutex();
                    _threadPool = new FastOS_ThreadPool(128 * 1024);
                    rc = true;
                }
                else
                {
                    fprintf(stderr,
                            "FastOS_Socket::InitializeServices() returned:\n[%s]\n",
                            errorMsg);
                }
            }
            else
                fprintf(stderr, "FastOS_File class initialization failed.\n");
        }
        else
            fprintf(stderr, "FastOS_Thread class initialization failed.\n");
    }
    else
        fprintf(stderr, "FastOS_PreThreadInit failed.\n");

    return rc;
}


void FastOS_ApplicationInterface::Cleanup ()
{
    if(_threadPool != NULL)
    {
        //      printf("Closing threadpool...\n");
        _threadPool->Close();
        //      printf("Deleting threadpool...\n");
        delete _threadPool;
        _threadPool = NULL;
    }

    if(_processListMutex != NULL)
    {
        delete _processListMutex;
        _processListMutex = NULL;
    }

    FastOS_Socket::CleanupServices();
    FastOS_File::CleanupClass();
    FastOS_Thread::CleanupClass();
}

int FastOS_ApplicationInterface::Entry (int argc, char **argv)
{
    int rc=255;

    _argc = argc;
    _argv = argv;

    if(Init())
    {
        rc = Main();
    }

    Cleanup();

    return rc;
}

void FastOS_ApplicationInterface::
OnReceivedIPCMessage (const void *data, size_t length)
{
    (void)data;
    (void)length;
    // Default dummy handler
}

void FastOS_ApplicationInterface::
AddChildProcess (FastOS_ProcessInterface *node)
{
    node->_prev = NULL;
    node->_next = _processList;

    if(_processList != NULL)
        _processList->_prev = node;

    _processList = node;
}

void FastOS_ApplicationInterface::
RemoveChildProcess (FastOS_ProcessInterface *node)
{
    if(node->_prev)
        node->_prev->_next = node->_next;
    else
        _processList = node->_next;

    if(node->_next)
    {
        node->_next->_prev = node->_prev;
        node->_next = NULL;
    }

    if(node->_prev != NULL)
        node->_prev = NULL;
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
