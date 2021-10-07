// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/index/i_index_writer.h>

namespace proton {

namespace test {

/**
 * Mock of the IIndexWriter interface used for unit testing.
 */
struct MockIndexWriter : public IIndexWriter
{
    IIndexManager::SP _idxMgr;
    MockIndexWriter() : _idxMgr() {}
    MockIndexWriter(const IIndexManager::SP &idxMgr) : _idxMgr(idxMgr) {}
    virtual const IIndexManager::SP &getIndexManager() const override { return _idxMgr; }
    virtual void put(search::SerialNum, const document::Document &, const search::DocumentIdT) override {}
    virtual void remove(search::SerialNum, const search::DocumentIdT) override {}
    virtual void commit(search::SerialNum, OnWriteDoneType) override {}
    virtual void heartBeat(search::SerialNum) override {}
    void compactLidSpace(search::SerialNum, const search::DocumentIdT) override {}
};

} // namespace test

} // namespace proton
