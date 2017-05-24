// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "ioerrorhandler.h"
#include "statebuf.h"
#include "statefile.h"
#include <atomic>

namespace search
{


IOErrorHandler *IOErrorHandler::_instance = nullptr;

namespace
{

std::atomic<int> nesting;

}

void
IOErrorHandler::trap()
{
    _instance = this;
    FastOS_File::SetFailedHandler(forward);
    _trapped = true;
}


void
IOErrorHandler::untrap()
{
#ifdef notyet
    FastOS_File::SetFailedHandler(nullptr);
#endif
    _trapped = false;
    _instance = nullptr;
}


void
IOErrorHandler::forward(const char *op, const char *file,
                        int error, int64_t offset, size_t len, ssize_t rlen)
{
    nesting++;
    IOErrorHandler *instance = _instance;
    if (instance) {
        instance->handle(op, file, error, offset, len, rlen);
    }
    nesting--;
}


void
IOErrorHandler::handle(const char *op, const char *file,
                       int error, int64_t offset, size_t len, ssize_t rlen)
{
    std::vector<char> buf(4096);
    StateBuf sb(&buf[0], buf.size());
    sb.appendKey("state") << "down";
    sb.appendTimestamp();
    sb.appendKey("operation") << op;
    sb.appendKey("file") << file;
    sb.appendKey("error") << error;
    sb.appendKey("offset") << offset;
    sb.appendKey("len") << len;
    sb.appendKey("rlen") << rlen;
    sb << '\n';
    if (_stateFile != nullptr) {
        _stateFile->addState(sb.base(), sb.size(), false);
    }
    _fired = true;
    sleep(3);
}


IOErrorHandler::IOErrorHandler(StateFile *stateFile)
    : _stateFile(stateFile),
      _trapped(false),
      _fired(false)
{
    trap();
}


IOErrorHandler::~IOErrorHandler()
{
    untrap();
    // Drain callbacks
    while (nesting != 0) {
        sleep(1);
    }
}

}
