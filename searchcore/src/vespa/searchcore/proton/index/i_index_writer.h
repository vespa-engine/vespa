// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/iindexmanager.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

/**
 * Interface for an index writer that handles writes in form of put and remove
 * to an underlying memory index.
 **/
class IIndexWriter {
public:
    typedef std::unique_ptr<IIndexWriter> UP;
    typedef std::shared_ptr<IIndexWriter> SP;
    using IIndexManager = searchcorespi::IIndexManager;
    using OnWriteDoneType = IIndexManager::OnWriteDoneType;
    using LidVector = std::vector<search::DocumentIdT>;

    virtual ~IIndexWriter() = default;

    virtual const std::shared_ptr<IIndexManager> &getIndexManager() const = 0;

    // feed interface
    virtual void put(search::SerialNum serialNum, const document::Document &doc, const search::DocumentIdT lid, OnWriteDoneType on_write_done) = 0;
    void remove(search::SerialNum serialNum, search::DocumentIdT lid) {
        LidVector lids;
        lids.push_back(lid);
        removeDocs(serialNum, std::move(lids));
    }
    virtual void removeDocs(search::SerialNum serialNum, LidVector lids) = 0;
    virtual void commit(search::SerialNum serialNum, OnWriteDoneType onWriteDone) = 0;
    virtual void heartBeat(search::SerialNum serialNum) = 0;
    virtual void compactLidSpace(search::SerialNum serialNum, const search::DocumentIdT lid) = 0;
};

} // namespace proton

