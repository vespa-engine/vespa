// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/flushengine/iflushhandler.h>
#include <vespa/vespalib/util/retain_guard.h>

namespace proton {

class DocumentDB;

class FlushHandlerProxy : public IFlushHandler
{
private:
    std::shared_ptr<DocumentDB> _documentDB;
    vespalib::RetainGuard _retainGuard;
public:
    FlushHandlerProxy(const std::shared_ptr<DocumentDB> &documentDB);

    ~FlushHandlerProxy() override;

    /**
     * Implements IFlushHandler.
     */
    std::vector<IFlushTarget::SP> getFlushTargets() override;
    SerialNum getCurrentSerialNumber() const override;
    void flushDone(SerialNum flushedSerial) override;
    void syncTls(SerialNum syncTo) override;
};

} // namespace proton

