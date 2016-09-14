// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/flushengine/iflushhandler.h>
#include "documentdb.h"

namespace proton {

class FlushHandlerProxy : public IFlushHandler
{
private:
    DocumentDB::SP _documentDB;
public:
    FlushHandlerProxy(const DocumentDB::SP &documentDB);

    virtual ~FlushHandlerProxy();

    /**
     * Implements IFlushHandler.
     */
    std::vector<IFlushTarget::SP> getFlushTargets() override;
    SerialNum getCurrentSerialNumber() const override;
    void flushDone(SerialNum flushedSerial) override;
    void syncTls(SerialNum syncTo) override;
};

} // namespace proton

