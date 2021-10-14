// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentmetastore.h"
#include <vespa/searchlib/common/tunefileinfo.h>

namespace proton {


/**
 * The result after initializing document meta store component in a
 * document sub database.
 */
class DocumentMetaStoreInitializerResult
{
private:
    DocumentMetaStore::SP _documentMetaStore;
    const search::TuneFileAttributes _tuneFile;

public:
    using SP = std::shared_ptr<DocumentMetaStoreInitializerResult>;

    DocumentMetaStoreInitializerResult(DocumentMetaStore::SP
                                       documentMetaStore_in,
                                       const search::TuneFileAttributes &
                                       tuneFile_in);

    virtual ~DocumentMetaStoreInitializerResult();

    DocumentMetaStore::SP documentMetaStore() const {
        return _documentMetaStore;
    }
    const search::TuneFileAttributes &tuneFile() const { return _tuneFile; }
};



} // namespace proton
