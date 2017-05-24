// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "flushhandlerproxy.h"
#include "documentdb.h"
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.flushhandlerproxy");

using searchcorespi::IFlushTarget;

namespace proton {

FlushHandlerProxy::FlushHandlerProxy(const DocumentDB::SP &documentDB)
    : IFlushHandler(documentDB->getDocTypeName().toString()),
      _documentDB(documentDB)
{
    _documentDB->retain();
}


FlushHandlerProxy::~FlushHandlerProxy()
{
    _documentDB->release();
}


std::vector<IFlushTarget::SP>
FlushHandlerProxy::getFlushTargets()
{
    return _documentDB->getFlushTargets();
}


IFlushHandler::SerialNum
FlushHandlerProxy::getCurrentSerialNumber() const
{
    return _documentDB->getCurrentSerialNumber();
}


void
FlushHandlerProxy::flushDone(SerialNum flushedSerial)
{
    _documentDB->flushDone(flushedSerial);
}


void
FlushHandlerProxy::syncTls(SerialNum syncTo)
{
    _documentDB->sync(syncTo);
}


} // namespace proton
