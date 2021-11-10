// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_index_writer.h"
#include <vespa/searchcore/proton/common/feeddebugger.h>

namespace proton {

class IndexWriter : public IIndexWriter,
                    private FeedDebugger {
private:
    IIndexManager::SP _mgr;

    bool ignoreOperation(search::SerialNum serialNum) const;

public:
    IndexWriter(const IIndexManager::SP &mgr);
    ~IndexWriter() override;
    const IIndexManager::SP & getIndexManager() const override { return _mgr; }

    void put(search::SerialNum serialNum, const document::Document &doc, const search::DocumentIdT lid, OnWriteDoneType on_write_done) override;
    void removeDocs(search::SerialNum serialNum, LidVector lids) override;
    void commit(search::SerialNum serialNum, OnWriteDoneType onWriteDone) override;

    void heartBeat(search::SerialNum serialNum) override;
    void compactLidSpace(search::SerialNum serialNum, const search::DocumentIdT lid) override;
};

} // namespace proton

