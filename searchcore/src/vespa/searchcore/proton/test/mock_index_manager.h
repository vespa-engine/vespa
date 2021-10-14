// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/iindexmanager.h>

namespace proton {

namespace test {

/**
 * Mock of the IIndexManager interface used for unit testing.
 */
struct MockIndexManager : public searchcorespi::IIndexManager
{
    virtual void putDocument(uint32_t, const Document &, SerialNum) override {}
    virtual void removeDocument(uint32_t, SerialNum) override {}
    virtual void commit(SerialNum, OnWriteDoneType) override {}
    virtual SerialNum getCurrentSerialNum() const override { return 0; }
    virtual SerialNum getFlushedSerialNum() const override { return 0; }
    virtual searchcorespi::IndexSearchable::SP getSearchable() const override {
        return searchcorespi::IndexSearchable::SP();
    }
    virtual search::SearchableStats getSearchableStats() const override {
        return search::SearchableStats();
    }
    virtual searchcorespi::IFlushTarget::List getFlushTargets() override {
        return searchcorespi::IFlushTarget::List();
    }
    virtual void setSchema(const Schema &, SerialNum) override {}
    virtual void heartBeat(SerialNum) override {}
    void compactLidSpace(uint32_t, SerialNum) override {}
    virtual void setMaxFlushed(uint32_t) override { }
};

} // namespace test

} // namespace proton
