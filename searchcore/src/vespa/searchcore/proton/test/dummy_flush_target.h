// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace proton::test {

struct DummyFlushTarget : public searchcorespi::LeafFlushTarget
{
    DummyFlushTarget(const vespalib::string &name) noexcept;
    DummyFlushTarget(const vespalib::string &name, const Type &type, const Component &component) noexcept;
    ~DummyFlushTarget() override;
    MemoryGain getApproxMemoryGain() const override { return MemoryGain(0, 0); }
    DiskGain getApproxDiskGain() const override { return DiskGain(0, 0); }
    SerialNum getFlushedSerialNum() const override { return 0; }
    Time getLastFlushTime() const override { return Time(); }
    searchcorespi::FlushTask::UP initFlush(SerialNum, std::shared_ptr<search::IFlushToken>) override {
        return searchcorespi::FlushTask::UP();
    }
    searchcorespi::FlushStats getLastFlushStats() const override {
        return searchcorespi::FlushStats();
    }

    uint64_t getApproxBytesToWriteToDisk() const override {
        return 0;
    }
};

}
