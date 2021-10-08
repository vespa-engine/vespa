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
    ~IndexWriter();
    /**
     * Implements IIndexWriter.
     */
    virtual const IIndexManager::SP & getIndexManager() const override { return _mgr; }

    virtual void put(search::SerialNum serialNum,
                     const document::Document &doc,
                     const search::DocumentIdT lid) override;
    virtual void remove(search::SerialNum serialNum,
                        const search::DocumentIdT lid) override;
    virtual void commit(search::SerialNum serialNum,
                        OnWriteDoneType onWriteDone) override;

    virtual void
    heartBeat(search::SerialNum serialNum) override;
    void compactLidSpace(search::SerialNum serialNum, const search::DocumentIdT lid) override;
};

} // namespace proton

