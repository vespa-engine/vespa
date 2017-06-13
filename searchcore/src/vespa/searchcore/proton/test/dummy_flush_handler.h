// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/flushengine/iflushhandler.h>

namespace proton {

namespace test {

/**
 * Default implementation used for testing.
 */
struct DummyFlushHandler : public IFlushHandler
{
    DummyFlushHandler(const vespalib::string &name)
        : IFlushHandler(name)
    {}

    // Implements IFlushHandler
    virtual std::vector<IFlushTarget::SP> getFlushTargets() override {
        return std::vector<IFlushTarget::SP>();
    }

    virtual SerialNum getCurrentSerialNumber() const override {
        return 0;
    }

    virtual void flushDone(SerialNum oldestSerial) override {
        (void) oldestSerial;
    }

    virtual void syncTls(SerialNum syncTo) override {
        (void) syncTo;
    }
};

} // namespace test

} // namespace proton
