// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_meta_store_initializer_result.h"

namespace proton {

DocumentMetaStoreInitializerResult::
DocumentMetaStoreInitializerResult(std::shared_ptr<DocumentMetaStore> documentMetaStore_in,
                                   const search::TuneFileAttributes & tuneFile_in)
    : _documentMetaStore(std::move(documentMetaStore_in)),
      _tuneFile(tuneFile_in)
{
}


DocumentMetaStoreInitializerResult::~DocumentMetaStoreInitializerResult() = default;

} // namespace proton
