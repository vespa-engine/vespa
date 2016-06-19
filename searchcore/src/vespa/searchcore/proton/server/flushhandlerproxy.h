// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/flushengine/iflushhandler.h>
#include "documentdb.h"

namespace proton {

class FlushHandlerProxy : public boost::noncopyable,
                          public IFlushHandler
{
private:
    DocumentDB::SP _documentDB;
public:
    FlushHandlerProxy(const DocumentDB::SP &documentDB);

    virtual
    ~FlushHandlerProxy(void);

    /**
     * Implements IFlushHandler.
     */
    virtual std::vector<IFlushTarget::SP>
    getFlushTargets(void);

    virtual SerialNum
    getCurrentSerialNumber(void) const;

    virtual void
    flushDone(SerialNum flushedSerial);

    virtual void
    syncTls(SerialNum syncTo);
};

} // namespace proton

