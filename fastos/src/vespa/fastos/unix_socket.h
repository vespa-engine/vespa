// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/socket.h>

class FastOS_UNIX_Socket : public FastOS_SocketInterface
{
public:
    ~FastOS_UNIX_Socket();

    bool Close () override;
    bool Shutdown() override;
    bool SetSoBlocking (bool blockingEnabled) override;
    ssize_t Read (void *readBuffer, size_t bufferSize) override;
    ssize_t Write (const void *writeBuffer, size_t bufferSize) override;

    static int GetLastError () { return errno; }
    static std::string getErrorString(int error);

    enum {
        ERR_ALREADY = EALREADY,               // New style error codes
        ERR_AGAIN = EAGAIN,
        ERR_INTR = EINTR,
        ERR_ISCONN = EISCONN,
        ERR_INPROGRESS = EINPROGRESS,
        ERR_WOULDBLOCK = EWOULDBLOCK,
        ERR_ADDRNOTAVAIL = EADDRNOTAVAIL,
        ERR_MFILE = FASTOS_EMFILE_VERIFIED,
        ERR_NFILE = FASTOS_ENFILE_VERIFIED,
        ERR_CONNRESET = ECONNRESET,

        ERR_EAGAIN = EAGAIN,                  // Old style error codes
        ERR_EINTR = EINTR,
        ERR_EISCONN = EISCONN,
        ERR_EINPROGRESS = EINPROGRESS,
        ERR_EWOULDBLOCK = EWOULDBLOCK,
        ERR_EADDRNOTAVAIL = EADDRNOTAVAIL,
        ERR_EMFILE = FASTOS_EMFILE_VERIFIED,
        ERR_ENFILE = FASTOS_ENFILE_VERIFIED
    };
};


