// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastlib/io/inputstream.h>
#include <vespa/fastlib/io/outputstream.h>
#include <vespa/fastos/socket.h>

class Fast_Socket : public FastOS_Socket,
                    public Fast_InputStream,
                    public Fast_OutputStream
{
private:
  Fast_Socket(const Fast_Socket&);
  Fast_Socket& operator=(const Fast_Socket&);

  FastOS_SocketEvent _event;
  int                _readTimeout;
  bool               _lastReadTimedOut;
  bool               _eof;

  public:

    /**
     * The Fast_Socket constructor creates a new socket instance
     *
     * @param  msReadTimeout    Number of milliseconds to wait for an event
     *                          before timeout. -1 means wait forever.
     */
    Fast_Socket(int msReadTimeout = -1 /* no timeout */)
        : _event(),
          _readTimeout(msReadTimeout),
          _lastReadTimedOut(false),
          _eof(false)
    {
    }

    ~Fast_Socket();

    ssize_t Write(const void *sourceBuffer, size_t bufferSize) override;
    ssize_t Read(void *targetBuffer, size_t bufferSize) override;
    bool Close() override;

    bool LastReadTimedOut()          { return _lastReadTimedOut; }
    bool SeenEOF()                   { return _eof; }

    Fast_InputStream  &GetInputStream()  { return *this; }
    Fast_OutputStream &GetOutputStream() { return *this; }


    // Implementation of Fast_InputStream and Fast_OutputStream interfaces

    void    Flush()                  override {                                           }
    ssize_t Available ()             override { return 0;                                 }
    ssize_t Skip (size_t skipNBytes) override { (void) skipNBytes; return -1;             }

    void Interrupt();
};






