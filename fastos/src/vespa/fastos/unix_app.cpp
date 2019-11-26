// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
******************************************************************************
* @author  Oivind H. Danielsen
* @date    Creation date: 2000-01-18
* @file
* Implementation of FastOS_UNIX_Application methods.
*****************************************************************************/

#include "app.h"
#include "time.h"
#include "process.h"
#include "unix_ipc.h"
#include <unistd.h>
#include <csignal>
#include <getopt.h>


FastOS_UNIX_Application::FastOS_UNIX_Application ()
    : _processStarter(nullptr),
      _ipcHelper(nullptr)
{
}

FastOS_UNIX_Application::~FastOS_UNIX_Application() = default;

extern "C"
{
extern char **environ;
};

int
FastOS_UNIX_Application::GetOpt (const char *optionsString,
            const char* &optionArgument,
            int &optionIndex)
{
    int rc = getopt(_argc, _argv, optionsString);
    optionArgument = optarg;
    optionIndex = optind;
    return rc;
}

int
FastOS_UNIX_Application::GetOptLong(const char *optionsString,
               const char* &optionArgument,
               int &optionIndex,
               const struct option *longopts,
               int *longindex)
{
    int rc = getopt_long(_argc, _argv, optionsString,
                         longopts,
                         longindex);

    optionArgument = optarg;
    optionIndex = optind;
    return rc;
}

void
FastOS_UNIX_Application::resetOptIndex(int optionIndex)
{
    optind = optionIndex;
}

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

        if (useProcessStarter()) {
            _processStarter = new FastOS_UNIX_ProcessStarter(this);
            if (!_processStarter->Start()) {
                rc = false;
                fprintf(stderr, "could not start FastOS_UNIX_ProcessStarter\n");
            }
        }
    } else {
        rc = false;
        fprintf(stderr, "FastOS_ApplicationInterface::PreThreadInit failed\n");
    }
    return rc;
}

bool FastOS_UNIX_Application::Init ()
{
    bool rc = false;

    if(FastOS_ApplicationInterface::Init())
    {
        int ipcDescriptor = -1;

        char *env = getenv("FASTOS_IPC_PARENT");
        if(env != nullptr)
        {
            int commaCount=0;
            int notDigitCount=0;
            char *p = env;
            while(*p != '\0')
            {
                if(*p == ',')
                    commaCount++;
                else if((*p < '0') || (*p > '9'))
                    notDigitCount++;
                p++;
            }

            if((commaCount == 2) && (notDigitCount == 0))
            {
                int ppid, gppid, descriptor;
                sscanf(env, "%d,%d,%d", &ppid, &gppid, &descriptor);

                if(ppid == getppid() && (descriptor != -1))
                {
                    ipcDescriptor = descriptor;
                }
            }
        }
        if (useIPCHelper()) {
            _ipcHelper = new FastOS_UNIX_IPCHelper(this, ipcDescriptor);
            GetThreadPool()->NewThread(_ipcHelper);
        }

        rc = true;
    }

    return rc;
}

void FastOS_UNIX_Application::Cleanup ()
{
    if(_ipcHelper != nullptr)
        _ipcHelper->Exit();

    if (_processStarter != nullptr) {
        {
            std::unique_lock<std::mutex> guard;
            if (_processListMutex) {
                guard = getProcessGuard();
            }
            _processStarter->Stop();
        }
        delete _processStarter;
        _processStarter = nullptr;
    }

    FastOS_ApplicationInterface::Cleanup();
}

FastOS_UNIX_ProcessStarter *
FastOS_UNIX_Application::GetProcessStarter ()
{
    return _processStarter;
}

void FastOS_UNIX_Application::
AddToIPCComm (FastOS_UNIX_Process *process)
{
    if(_ipcHelper != nullptr)
        _ipcHelper->AddProcess(process);
}

void FastOS_UNIX_Application::
RemoveFromIPCComm (FastOS_UNIX_Process *process)
{
    if(_ipcHelper != nullptr)
        _ipcHelper->RemoveProcess(process);
}
