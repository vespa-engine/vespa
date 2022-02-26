// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/iindexmanager.h>

namespace proton::test {

/**
 * Mock of the IIndexManager interface used for unit testing.
 */
struct MockIndexManager : public searchcorespi::IIndexManager
{
    ~MockIndexManager() override;
    void putDocument(uint32_t, const Document &, SerialNum, OnWriteDoneType) override {}
    void removeDocuments(LidVector, SerialNum) override {}
    void commit(SerialNum, OnWriteDoneType) override {}
    SerialNum getCurrentSerialNum() const override { return 0; }
    SerialNum getFlushedSerialNum() const override { return 0; }
    searchcorespi::IndexSearchable::SP getSearchable() const override {
        return searchcorespi::IndexSearchable::SP();
    }
    search::SearchableStats getSearchableStats() const override {
        return search::SearchableStats();
    }
    searchcorespi::IFlushTarget::List getFlushTargets() override {
        return searchcorespi::IFlushTarget::List();
    }
    void setSchema(const Schema &, SerialNum) override {}
    void heartBeat(SerialNum) override {}
    void compactLidSpace(uint32_t, SerialNum) override {}
    void setMaxFlushed(uint32_t) override { }
};

}
