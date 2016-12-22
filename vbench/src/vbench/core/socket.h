// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string.h"
#include "stream.h"
#include "simple_buffer.h"
#include <memory>

class FastOS_SocketInterface;

namespace vbench {

class Socket : public Stream
{
private:
    std::unique_ptr<FastOS_SocketInterface> _socket;
    SimpleBuffer                            _input;
    SimpleBuffer                            _output;
    Taint                                   _taint;
    bool                                    _eof;

public:
    Socket(std::unique_ptr<FastOS_SocketInterface> socket);
    Socket(const string host, int port);
    virtual ~Socket();
    virtual bool eof() const { return _eof; }
    virtual Memory obtain(size_t bytes, size_t lowMark);
    virtual Input &evict(size_t bytes);
    virtual WritableMemory reserve(size_t bytes);
    virtual Output &commit(size_t bytes, size_t hiMark);
    virtual const Taint &tainted() const { return _taint; }
};

} // namespace vbench

