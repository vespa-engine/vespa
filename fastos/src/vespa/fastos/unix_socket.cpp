// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/socket.h>

FastOS_UNIX_Socket::~FastOS_UNIX_Socket()
{
    FastOS_UNIX_Socket::Close();
}

bool FastOS_UNIX_Socket::Close()
{
    bool rc=true;

    if (ValidHandle()) {
        CleanupEvents();
        rc = (0 == close(_socketHandle));
        _socketHandle = -1;
    }

    return rc;
}

bool FastOS_UNIX_Socket::Shutdown()
{
    bool rc=true;

    if (ValidHandle()) {
        if(_socketEvent != NULL) {
            EnableWriteEvent(false);
        }
        rc = (0 == shutdown(_socketHandle, SHUT_WR));
    }

    return rc;
}

bool FastOS_UNIX_Socket::SetSoBlocking (bool blockingEnabled)
{
    bool rc=false;

    if (CreateIfNoSocketYet()) {
        int flags = fcntl(_socketHandle, F_GETFL, NULL);

        if (flags >= 0) {
            if (blockingEnabled) {
                flags &= ~O_NONBLOCK; // clear nonblocking
            } else {
                flags |= O_NONBLOCK;  // set nonblocking
            }

            if (fcntl(_socketHandle, F_SETFL, flags) >= 0) {
                rc = true;
            }
        }
    }

    return rc;
}

ssize_t FastOS_UNIX_Socket::Write (const void *writeBuffer, size_t bufferSize)
{
    assert(ValidHandle());

    ssize_t got;
    do {
        got = ::write(_socketHandle, writeBuffer, bufferSize);
    } while (got<0 && errno == EINTR);  // caught interrupt; nonBlocking sock.s

    return got;
}


ssize_t FastOS_UNIX_Socket::Read (void *readBuffer, size_t bufferSize)
{
    assert(ValidHandle());

    ssize_t got;
    do {
        got = ::read(_socketHandle, readBuffer, bufferSize);
    } while (got<0 && errno == EINTR);  // caught interrupt; nonBlocking sock.s

    return got;
}


std::string
FastOS_UNIX_Socket::getErrorString(int error)
{
    char errorBuf[100];
    const char *errorString = strerror_r(error, errorBuf, sizeof(errorBuf));
    return std::string(errorString);
}


bool FastOS_SocketEventObjects::Init (FastOS_SocketEvent *event)
{
    (void)event;

    _wakeUpPipe[0] = -1;
    _wakeUpPipe[1] = -1;

    if (pipe(_wakeUpPipe) == 0) {
        int flags;
        flags = fcntl(_wakeUpPipe[0], F_GETFL, 0);
        if (flags != -1) {
            flags |= O_NONBLOCK;
            fcntl(_wakeUpPipe[0], F_SETFL, flags);
        }
        flags = fcntl(_wakeUpPipe[1], F_GETFL, 0);
        if (flags != -1) {
            flags |= O_NONBLOCK;
            fcntl(_wakeUpPipe[1], F_SETFL, flags);
        }
        return true;
    } else {
        return false;
    }
}

void FastOS_SocketEventObjects::Cleanup ()
{
    if(_wakeUpPipe[0] != -1) {
        close(_wakeUpPipe[0]);
    }
    if(_wakeUpPipe[1] != -1) {
        close(_wakeUpPipe[1]);
    }
}
