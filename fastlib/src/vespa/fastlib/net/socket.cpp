// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket.h"

Fast_Socket::~Fast_Socket()
{
}

bool
Fast_Socket::Close(void)
{
    return FastOS_Socket::Close();
}

ssize_t
Fast_Socket::Read(void* targetBuffer, size_t bufferSize)
{
    bool oldReadEventEnabled = _readEventEnabled;
    FastOS_SocketEvent* oldSocketEvent = _socketEvent;
    void* oldEventAttribute = _eventAttribute;
    bool err = false;
    bool eventOcc = false;
    ssize_t rtrn = -1;

    errno = 0;
    _lastReadTimedOut = false;

    if (_event.GetCreateSuccess() == false)
        return rtrn;

    if (SetSocketEvent(&_event) == true)
    {
        EnableReadEvent(true);
        eventOcc  = _event.Wait(err, _readTimeout);
        if (eventOcc == true && err == false)
        {
            rtrn = FastOS_Socket::Read(targetBuffer, bufferSize);
            _eof = (rtrn == 0);
        }
        else if(!eventOcc && !err)
        {
            _lastReadTimedOut = true;
        }
    }

    SetSocketEvent(oldSocketEvent, oldEventAttribute);

    if (oldSocketEvent != 0)
        EnableReadEvent(oldReadEventEnabled);

    return rtrn;
}

void
Fast_Socket::Interrupt()
{
    _event.AsyncWakeUp();
}

ssize_t
Fast_Socket::Write(const void *sourceBuffer, size_t bufferSize)
{
    return FastOS_Socket::Write(sourceBuffer, bufferSize);
}
