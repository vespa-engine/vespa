// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string.h"
#include "stream.h"
#include <vespa/vespalib/data/simple_buffer.h>
#include <memory>

class FastOS_SocketInterface;

namespace vbench {

using Input = vespalib::Input;
using Memory = vespalib::Memory;
using Output = vespalib::Output;
using SimpleBuffer = vespalib::SimpleBuffer;
using WritableMemory = vespalib::WritableMemory;

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
    virtual Memory obtain();
    virtual Input &evict(size_t bytes);
    virtual WritableMemory reserve(size_t bytes);
    virtual Output &commit(size_t bytes);
    virtual const Taint &tainted() const { return _taint; }
};

} // namespace vbench

