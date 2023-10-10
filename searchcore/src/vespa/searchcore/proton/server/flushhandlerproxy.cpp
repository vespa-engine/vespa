// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flushhandlerproxy.h"
#include "documentdb.h"

using searchcorespi::IFlushTarget;

namespace proton {

FlushHandlerProxy::FlushHandlerProxy(const DocumentDB::SP &documentDB)
    : IFlushHandler(documentDB->getDocTypeName().toString()),
      _documentDB(documentDB),
      _retainGuard(_documentDB->retain())
{ }


FlushHandlerProxy::~FlushHandlerProxy() = default;


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
