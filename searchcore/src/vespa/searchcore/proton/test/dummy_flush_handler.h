// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/flushengine/iflushhandler.h>

namespace proton::test {

/**
 * Default implementation used for testing.
 */
struct DummyFlushHandler : public IFlushHandler
{
    DummyFlushHandler(const vespalib::string &name) noexcept
        : IFlushHandler(name)
    {}

    std::vector<IFlushTarget::SP> getFlushTargets() override {
        return std::vector<IFlushTarget::SP>();
    }

    SerialNum getCurrentSerialNumber() const override {
        return 0;
    }

    void flushDone(SerialNum oldestSerial) override {
        (void) oldestSerial;
    }

    void syncTls(SerialNum syncTo) override {
        (void) syncTo;
    }
};

}
