// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    virtual ~IIndexWriter() {}

    virtual const std::shared_ptr<IIndexManager> &getIndexManager() const = 0;

    // feed interface
    virtual void put(search::SerialNum serialNum, const document::Document &doc, const search::DocumentIdT lid) = 0;
    virtual void remove(search::SerialNum serialNum, const search::DocumentIdT lid) = 0;
    virtual void commit(search::SerialNum serialNum, OnWriteDoneType onWriteDone) = 0;
    virtual void heartBeat(search::SerialNum serialNum) = 0;
};

} // namespace proton

