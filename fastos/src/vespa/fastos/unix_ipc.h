// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "app.h"
#include "process.h"
#include "thread.h"
#include <poll.h>

class FastOS_RingBuffer;

class FastOS_UNIX_IPCHelper : public FastOS_Runnable
{
private:
    FastOS_UNIX_IPCHelper(const FastOS_UNIX_IPCHelper&);
    FastOS_UNIX_IPCHelper& operator=(const FastOS_UNIX_IPCHelper&);

protected:
    std::mutex    _lock;
    volatile bool _exitFlag;
    FastOS_ApplicationInterface *_app;

    FastOS_UNIX_Process::DescriptorHandle _appParentIPCDescriptor;

    int _wakeupPipe[2];

    bool DoWrite (FastOS_UNIX_Process::DescriptorHandle &desc);
    bool DoRead (FastOS_UNIX_Process::DescriptorHandle &desc);
    bool SetBlocking (int fileDescriptor, bool doBlock);
    void BuildPollCheck (bool isRead, int filedes, FastOS_RingBuffer *buffer, bool *check);
    void BuildPollArray(pollfd **fds, unsigned int *nfds, unsigned int *allocnfds);
    bool SavePollArray(pollfd *fds, unsigned int nfds);
    void PerformAsyncIO ();
    void PerformAsyncIPCIO ();
    void BuildPollChecks();
    void PipeData (FastOS_UNIX_Process *process, FastOS_UNIX_Process::DescriptorType type);
    void RemoveClosingProcesses();

public:
    FastOS_UNIX_IPCHelper (FastOS_ApplicationInterface *app, int appDescriptor);
    ~FastOS_UNIX_IPCHelper ();
    void Run (FastOS_ThreadInterface *thisThread, void *arg) override;
    void NotifyProcessListChange ();
    void AddProcess (FastOS_UNIX_Process *xproc);
    void RemoveProcess (FastOS_UNIX_Process *xproc);
    void Exit ();
};
