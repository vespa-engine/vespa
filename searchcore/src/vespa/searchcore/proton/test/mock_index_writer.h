// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/index/i_index_writer.h>

namespace proton::test {

/**
 * Mock of the IIndexWriter interface used for unit testing.
 */
struct MockIndexWriter : public IIndexWriter
{
    IIndexManager::SP _idxMgr;
    MockIndexWriter() : _idxMgr() {}
    MockIndexWriter(const IIndexManager::SP &idxMgr) : _idxMgr(idxMgr) {}
    const IIndexManager::SP &getIndexManager() const override { return _idxMgr; }
    void put(search::SerialNum, const document::Document &, const search::DocumentIdT, OnWriteDoneType) override {}
    void removeDocs(search::SerialNum, LidVector) override {}
    void commit(search::SerialNum, OnWriteDoneType) override {}
    void heartBeat(search::SerialNum) override {}
    void compactLidSpace(search::SerialNum, const search::DocumentIdT) override {}
};

}
