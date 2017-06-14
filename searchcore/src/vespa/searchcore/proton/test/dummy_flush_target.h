// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace proton {

namespace test {

struct DummyFlushTarget : public searchcorespi::IFlushTarget
{
    DummyFlushTarget(const vespalib::string &name)
        : searchcorespi::IFlushTarget(name)
    {}
    DummyFlushTarget(const vespalib::string &name,
                     const Type &type,
                     const Component &component)
        : searchcorespi::IFlushTarget(name, type, component)
    {}
    // Implements searchcorespi::IFlushTarget
    virtual MemoryGain getApproxMemoryGain() const override { return MemoryGain(0, 0); }
    virtual DiskGain getApproxDiskGain() const override { return DiskGain(0, 0); }
    virtual SerialNum getFlushedSerialNum() const override { return 0; }
    virtual Time getLastFlushTime() const override { return Time(); }
    virtual bool needUrgentFlush() const override { return false; }
    virtual searchcorespi::FlushTask::UP initFlush(SerialNum) override {
        return searchcorespi::FlushTask::UP();
    }
    virtual searchcorespi::FlushStats getLastFlushStats() const override {
        return searchcorespi::FlushStats();
    }

    virtual uint64_t getApproxBytesToWriteToDisk() const override {
        return 0;
    }
};

} // namespace test

} // namespace proton

