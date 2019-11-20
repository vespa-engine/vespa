// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "unix_ipc.h"
#include "ringbuffer.h"
#include <cassert>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <memory>
#include <future>

FastOS_UNIX_IPCHelper::
FastOS_UNIX_IPCHelper (FastOS_ApplicationInterface *app, int descriptor)
    : _lock(),
      _exitFlag(false),
      _app(app),
      _appParentIPCDescriptor()
{
    _appParentIPCDescriptor._fd = descriptor;
    _wakeupPipe[0] = -1;
    _wakeupPipe[1] = -1;

    if(pipe(_wakeupPipe) != 0) {
        perror("pipe wakeuppipe");
        exit(1);
    } else {
        SetBlocking(_wakeupPipe[0], false);
        SetBlocking(_wakeupPipe[1], true);
    }

    if(_appParentIPCDescriptor._fd != -1) {
        _appParentIPCDescriptor._readBuffer.reset(new FastOS_RingBuffer(16384));
        _appParentIPCDescriptor._writeBuffer.reset(new FastOS_RingBuffer(16384));

        SetBlocking(_appParentIPCDescriptor._fd, false);
    }
}

FastOS_UNIX_IPCHelper::~FastOS_UNIX_IPCHelper ()
{
    if(_wakeupPipe[0] != -1) {
        close(_wakeupPipe[0]);
    }
    if(_wakeupPipe[1] != -1) {
        close(_wakeupPipe[1]);
    }
    if(_appParentIPCDescriptor._fd != -1) {
        close(_appParentIPCDescriptor._fd);
    }
    _appParentIPCDescriptor._readBuffer.reset();
    _appParentIPCDescriptor._writeBuffer.reset();
}


bool FastOS_UNIX_IPCHelper::
DoWrite(FastOS_UNIX_Process::DescriptorHandle &desc)
{
    bool rc = true;
    FastOS_RingBuffer *buffer = desc._writeBuffer.get();

    auto bufferGuard = buffer->getGuard();
    int writeBytes = buffer->GetReadSpace();
    if(writeBytes > 0)
    {
        int bytesWritten;
        do
        {
            bytesWritten = write(desc._fd,
                                 buffer->GetReadPtr(),
                                 writeBytes);
        } while(bytesWritten < 0 && errno == EINTR);

        if(bytesWritten > 0)
            buffer->Consume(bytesWritten);
        else if(bytesWritten < 0)
        {
            desc.CloseHandle();
            perror("FastOS_UNIX_IPCHelper::DoWrite");
            rc = false;
        }
        else if(bytesWritten == 0)
            desc.CloseHandle();
    }
    return rc;
}

bool FastOS_UNIX_IPCHelper::
DoRead (FastOS_UNIX_Process::DescriptorHandle &desc)
{
    bool rc = true;

    FastOS_RingBuffer *buffer = desc._readBuffer.get();

    auto bufferGuard = buffer->getGuard();
    int readBytes = buffer->GetWriteSpace();
    if(readBytes > 0) {
        int bytesRead;
        do {
            bytesRead = read(desc._fd, buffer->GetWritePtr(), readBytes);
        } while(bytesRead < 0 && errno == EINTR);

        if (bytesRead > 0) {
            buffer->Produce(bytesRead);
        } else if(bytesRead < 0) {
            desc.CloseHandle();
            perror("FastOS_UNIX_IPCHelper::DoRead");
            rc = false;
        } else if(bytesRead == 0) {
            desc.CloseHandle();
        }
    }

    return rc;
}

bool FastOS_UNIX_IPCHelper::
SetBlocking (int fileDescriptor, bool doBlock)
{
    bool rc=false;

    int flags = fcntl(fileDescriptor, F_GETFL, nullptr);
    if (flags != -1)
    {
        if(doBlock)
            flags &= ~O_NONBLOCK;
        else
            flags |= O_NONBLOCK;
        rc = (fcntl(fileDescriptor, F_SETFL, flags) != -1);
    }
    return rc;
}

void FastOS_UNIX_IPCHelper::
BuildPollCheck(bool isRead, int filedes,
               FastOS_RingBuffer *buffer, bool *check)
{
    if(buffer == nullptr ||
       filedes < 0 ||
       buffer->GetCloseFlag()) {
        *check = false;
        return;
    }

    bool setIt = false;
    if(isRead)
        setIt = (buffer->GetWriteSpace() > 0);
    else
        setIt = (buffer->GetReadSpace() > 0);
    *check = setIt;
}


void FastOS_UNIX_IPCHelper::
PerformAsyncIO()
{
    FastOS_ProcessInterface *node;
    for(node = _app->GetProcessList(); node != nullptr; node = node->_next)
    {
        FastOS_UNIX_Process *xproc = static_cast<FastOS_UNIX_Process *>(node);

        for(int type=0; type < int(FastOS_UNIX_Process::TYPE_READCOUNT); type++)
        {
            FastOS_UNIX_Process::DescriptorType type_ =
                FastOS_UNIX_Process::DescriptorType(type);
            FastOS_UNIX_Process::DescriptorHandle &desc =
                xproc->GetDescriptorHandle(type_);
            if (desc._canRead)
                (void) DoRead(desc);
            if (desc._canWrite)
                (void) DoWrite(desc);
        }
    }
}

void FastOS_UNIX_IPCHelper::
PerformAsyncIPCIO()
{
    FastOS_UNIX_Process::DescriptorHandle &desc = _appParentIPCDescriptor;
    if (desc._canRead)
        (void) DoRead(desc);
    if (desc._canWrite)
        (void) DoWrite(desc);
}


void FastOS_UNIX_IPCHelper::
BuildPollChecks()
{
    FastOS_ProcessInterface *node;
    for(node = _app->GetProcessList(); node != nullptr; node = node->_next)
    {
        FastOS_UNIX_Process *xproc = static_cast<FastOS_UNIX_Process *>(node);

        for(int type=0; type < int(FastOS_UNIX_Process::TYPE_READCOUNT); type++)
        {
            FastOS_UNIX_Process::DescriptorType type_ =
                FastOS_UNIX_Process::DescriptorType(type);
            FastOS_UNIX_Process::DescriptorHandle &desc =
                xproc->GetDescriptorHandle(type_);
            BuildPollCheck(false, desc._fd, desc._writeBuffer.get(), &desc._wantWrite);
            BuildPollCheck(true,  desc._fd, desc._readBuffer.get(), &desc._wantRead);
        }
    }

    if(_appParentIPCDescriptor._writeBuffer.get() != nullptr)
        BuildPollCheck(false, _appParentIPCDescriptor._fd,
                       _appParentIPCDescriptor._writeBuffer.get(),
                       &_appParentIPCDescriptor._wantWrite);
    if(_appParentIPCDescriptor._readBuffer.get() != nullptr)
        BuildPollCheck(true, _appParentIPCDescriptor._fd,
                       _appParentIPCDescriptor._readBuffer.get(),
                       &_appParentIPCDescriptor._wantRead);
}


static pollfd *
__attribute__((__noinline__))
ResizePollArray(pollfd **fds, unsigned int *allocnfds)
{
    pollfd *newfds;
    unsigned int newallocnfds;

    if (*allocnfds == 0)
        newallocnfds = 16;
    else
        newallocnfds = *allocnfds * 2;
    newfds = static_cast<pollfd *>(malloc(newallocnfds * sizeof(pollfd)));
    assert(newfds != nullptr);

    if (*allocnfds > 0)
        memcpy(newfds, *fds, sizeof(pollfd) * *allocnfds);

    if (*fds != nullptr)
        free(*fds);

    *fds = newfds;
    newfds += *allocnfds;
    *allocnfds = newallocnfds;
    return newfds;
}

void
FastOS_UNIX_IPCHelper::
BuildPollArray(pollfd **fds, unsigned int *nfds, unsigned int *allocnfds)
{
    FastOS_ProcessInterface *node;
    pollfd *rfds;
    const pollfd *rfdsEnd;
    int pollIdx;

    rfds = *fds;
    rfdsEnd = *fds + *allocnfds;

    if (rfds >= rfdsEnd) {
        rfds = ResizePollArray(fds,
                               allocnfds);
        rfdsEnd = *fds + *allocnfds;
    }
    rfds->fd = _wakeupPipe[0];
    rfds->events = POLLIN;
    rfds->revents = 0;
    rfds++;
    pollIdx = 1;
    for(node = _app->GetProcessList(); node != nullptr; node = node->_next)
    {
        FastOS_UNIX_Process *xproc = static_cast<FastOS_UNIX_Process *>(node);

        for(int type=0; type < int(FastOS_UNIX_Process::TYPE_READCOUNT); type++)
        {
            FastOS_UNIX_Process::DescriptorType type_ =
                FastOS_UNIX_Process::DescriptorType(type);
            FastOS_UNIX_Process::DescriptorHandle &desc =
                xproc->GetDescriptorHandle(type_);

            if (desc._fd >= 0 &&
                (desc._wantRead || desc._wantWrite)) {
                if (rfds >= rfdsEnd) {
                    rfds = ResizePollArray(fds,
                            allocnfds);
                    rfdsEnd = *fds + *allocnfds;
                }
                rfds->fd = desc._fd;
                rfds->events = 0;
                if (desc._wantRead)
                    rfds->events |= POLLRDNORM;
                if (desc._wantWrite)
                    rfds->events |= POLLWRNORM;
                rfds->revents = 0;
                desc._pollIdx = pollIdx;
                rfds++;
                pollIdx++;
            } else {
                desc._pollIdx = -1;
                desc._canRead = false;
                desc._canWrite = false;
            }
        }
    }

    FastOS_UNIX_Process::DescriptorHandle &desc2 = _appParentIPCDescriptor;

    if (desc2._fd >= 0 &&
        (desc2._wantRead || desc2._wantWrite)) {
        if (rfds >= rfdsEnd) {
            rfds = ResizePollArray(fds,
                                   allocnfds);
            rfdsEnd = *fds + *allocnfds;
        }
        rfds->fd = desc2._fd;
        rfds->events = 0;
        if (desc2._wantRead)
            rfds->events |= POLLRDNORM;
        if (desc2._wantWrite)
            rfds->events |= POLLWRNORM;
        rfds->revents = 0;
        desc2._pollIdx = pollIdx;
        rfds++;
        pollIdx++;
    } else {
        desc2._pollIdx = -1;
        desc2._canRead = false;
        desc2._canWrite = false;
    }
    *nfds = rfds - *fds;
}


bool
FastOS_UNIX_IPCHelper::
SavePollArray(pollfd *fds, unsigned int nfds)
{
    FastOS_ProcessInterface *node;

    for(node = _app->GetProcessList(); node != nullptr; node = node->_next)
    {
        FastOS_UNIX_Process *xproc = static_cast<FastOS_UNIX_Process *>(node);

        for(int type=0; type < int(FastOS_UNIX_Process::TYPE_READCOUNT); type++)
        {
            FastOS_UNIX_Process::DescriptorType type_ =
                FastOS_UNIX_Process::DescriptorType(type);
            FastOS_UNIX_Process::DescriptorHandle &desc =
                xproc->GetDescriptorHandle(type_);

            if (desc._fd >= 0 &&
                static_cast<unsigned int>(desc._pollIdx) < nfds) {
                int revents = fds[desc._pollIdx].revents;

                if (desc._wantRead &&
                    (revents &
                     (POLLIN | POLLRDNORM | POLLERR | POLLHUP | POLLNVAL)) != 0)
                    desc._canRead = true;
                else
                    desc._canRead = false;
                if (desc._wantWrite &&
                    (revents &
                     (POLLOUT | POLLWRNORM | POLLWRBAND | POLLERR | POLLHUP |
                      POLLNVAL)) != 0)
                    desc._canWrite = true;
                else
                    desc._canWrite = false;
            }
        }
    }

    FastOS_UNIX_Process::DescriptorHandle &desc2 = _appParentIPCDescriptor;

    if (desc2._fd >= 0 &&
        static_cast<unsigned int>(desc2._pollIdx) < nfds) {
        int revents = fds[desc2._pollIdx].revents;

        if ((revents &
             (POLLIN | POLLRDNORM | POLLERR | POLLHUP | POLLNVAL)) != 0)
            desc2._canRead = true;
        else
            desc2._canRead = false;
        if ((revents &
             (POLLOUT | POLLWRNORM | POLLWRBAND | POLLERR | POLLHUP |
              POLLNVAL)) != 0)
            desc2._canWrite = true;
        else
            desc2._canWrite = false;
    }

    if ((fds[0].revents & (POLLIN | POLLERR | POLLHUP)) != 0)
        return true;
    else
        return false;
}


void FastOS_UNIX_IPCHelper::
RemoveClosingProcesses()
{
    // We assume that not updating maxFD isn't harmless.

    FastOS_ProcessInterface *node, *next;

    for(node = _app->GetProcessList(); node != nullptr; node = next)
    {
        int type;

        next = node->_next;
        FastOS_UNIX_Process *xproc = static_cast<FastOS_UNIX_Process *>(node);

        bool stillBusy = false;
        if(!xproc->GetKillFlag())
            for(type=0; type < FastOS_UNIX_Process::TYPE_READCOUNT; type++)
            {
                FastOS_UNIX_Process::DescriptorType type_;

                type_ = static_cast<FastOS_UNIX_Process::DescriptorType>(type);

                FastOS_UNIX_Process::DescriptorHandle &desc =
                    xproc->GetDescriptorHandle(type_);

                if (desc._fd != -1)
                {
                    if((type_ == FastOS_UNIX_Process::TYPE_STDOUT) ||
                       (type_ == FastOS_UNIX_Process::TYPE_STDERR) ||
                       desc._wantWrite)
                    {
                        // We still want to use this socket.
                        // Make sure we don't close the socket yet.
                        stillBusy = true;
                        break;
                    }
                }
            }

        if(!stillBusy)
        {
            if (xproc->_closing) {
                // We already have the process lock at this point,
                // so modifying the list is safe.
                _app->RemoveChildProcess(node);

                for(type=0; type < FastOS_UNIX_Process::TYPE_READCOUNT; type++)
                {
                    FastOS_UNIX_Process::DescriptorHandle &desc =
                        xproc->GetDescriptorHandle(FastOS_UNIX_Process::DescriptorType(type));
                    if(desc._fd != -1)
                    {
                        // No more select on this one.
                        // We already know wantWrite is not set
                        if (desc._wantRead)
                            desc._wantRead = false;
                    }
                }

                // The process destructor can now proceed
                auto closingPromise(std::move(xproc->_closing));
                closingPromise->set_value();
            }
        }
    }
}


void FastOS_UNIX_IPCHelper::
Run(FastOS_ThreadInterface *thisThread, void *arg)
{
    (void)arg;
    (void)thisThread;

    FastOS_ProcessInterface *node;
    pollfd *fds;
    unsigned int nfds;
    unsigned int allocnfds;

    fds = nullptr;
    nfds = 0;
    allocnfds = 0;
    for(;;)
    {
        // Deliver messages to from child processes and parent.
        {
            auto guard = _app->getProcessGuard();
            for(node = _app->GetProcessList(); node != nullptr; node = node->_next)
            {
                FastOS_UNIX_Process *xproc = static_cast<FastOS_UNIX_Process *>(node);
                PipeData(xproc, FastOS_UNIX_Process::TYPE_STDOUT);
                PipeData(xproc, FastOS_UNIX_Process::TYPE_STDERR);
            }

            // Setup file descriptor sets for the next select() call
            BuildPollChecks();

            // Close and signal closing processes
            RemoveClosingProcesses();

            BuildPollArray(&fds, &nfds, &allocnfds);
        }
        bool exitFlag = false;
        {
            std::lock_guard<std::mutex> guard(_lock);
            exitFlag = _exitFlag;
        }
        if (exitFlag)
        {
            if (_appParentIPCDescriptor._fd != -1)
            {
                if(_appParentIPCDescriptor._wantWrite)
                {
                    //               printf("still data to write\n");
                }
                else
                {
                    //               printf("no more data to write, exitting\n");
                    break;
                }
            }
            else
                break;
        }

        for (;;)
        {
            int pollRc =
                poll(fds, nfds, -1);

            if(pollRc == -1)
            {
                int wasErrno = errno;

                if(wasErrno == EINTR)
                {
                    continue;
                }

                perror("FastOS_UNIX_IPCHelper::RunAsync select failure");
                printf("errno = %d\n", wasErrno);
                for(unsigned int i = 0; i < nfds; i++)
                {
                    if ((fds[i].events & POLLIN) != 0)
                        printf("Read %d\n", fds[i].fd);
                    if ((fds[i].events & POLLOUT) != 0)
                        printf("Write %d\n", fds[i].fd);
                }
                exit(1);
            }
            else
                break;
        }

        bool woken = false;
        {
            auto guard = _app->getProcessGuard();
            woken = SavePollArray(fds, nfds);
            // Do actual IO (based on file descriptor sets and buffer contents)
            PerformAsyncIO();
        }
        PerformAsyncIPCIO();

        // Did someone want to wake us up from the poll() call?
        if (woken) {
            char dummy;
	    ssize_t nbrfp = read(_wakeupPipe[0], &dummy, 1);
	    if (nbrfp != 1) {
	        perror("FastOS_UNIX_IPCHelper wakeupPipe read failed");
	    }
        }
    }
    free(fds);

    delete this;
}

void
FastOS_UNIX_IPCHelper::NotifyProcessListChange ()
{
    char dummy = 'x';
    ssize_t nbwtp = write(_wakeupPipe[1], &dummy, 1);
    if (nbwtp != 1) {
	perror("FastOS_UNIX_IPCHelper: write to wakeupPipe failed");
    }
}

void
FastOS_UNIX_IPCHelper::Exit ()
{
    std::lock_guard<std::mutex> guard(_lock);
    _exitFlag = true;
    NotifyProcessListChange();
}

void
FastOS_UNIX_IPCHelper::AddProcess (FastOS_UNIX_Process *xproc)
{
    bool newStream = false;
    for(int type=0; type < int(FastOS_UNIX_Process::TYPE_READCOUNT); type++)
    {
        FastOS_UNIX_Process::DescriptorType type_ = FastOS_UNIX_Process::DescriptorType(type);
        FastOS_UNIX_Process::DescriptorHandle &desc = xproc->GetDescriptorHandle(type_);

        if (desc._fd != -1) {
            newStream = true;
            SetBlocking(desc._fd, false);
        }
    }
    if(newStream)
        NotifyProcessListChange();
}

void
FastOS_UNIX_IPCHelper::RemoveProcess (FastOS_UNIX_Process *xproc)
{
    auto closePromise = std::make_unique<std::promise<void>>();
    auto closeFuture = closePromise->get_future();
    xproc->_closing = std::move(closePromise);
    NotifyProcessListChange();
    closeFuture.wait();
}

void
FastOS_UNIX_IPCHelper::PipeData(FastOS_UNIX_Process *process, FastOS_UNIX_Process::DescriptorType type)
{
    FastOS_UNIX_Process::DescriptorHandle &desc = process->GetDescriptorHandle(type);
    FastOS_RingBuffer *buffer = desc._readBuffer.get();
    if(buffer == nullptr)
        return;

    FastOS_ProcessRedirectListener *listener = process->GetListener(type);
    if(listener == nullptr)
        return;

    auto bufferGuard = buffer->getGuard();

    unsigned int readSpace;
    while((readSpace = buffer->GetReadSpace()) > 0) {
        listener->OnReceiveData(buffer->GetReadPtr(), size_t(readSpace));
        buffer->Consume(readSpace);
    }

    if(buffer->GetCloseFlag())
        process->CloseListener(type);
}
