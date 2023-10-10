// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_initializer_result.h"

using searchcorespi::IIndexManager;

namespace proton {

DocumentSubDbInitializerResult::DocumentSubDbInitializerResult()
    : _documentMetaStore(std::make_shared<DocumentMetaStoreInitializerResult::SP>
                         ()),
      _summaryManager(std::make_shared<SummaryManager::SP>()),
      _attributeManager(std::make_shared<AttributeManager::SP>()),
      _indexManager(std::make_shared<IIndexManager::SP>()),
      _flushConfig()
{
}

void
DocumentSubDbInitializerResult::
setFlushConfig(const DocumentDBFlushConfig &flushConfig)
{
    _flushConfig = flushConfig;
}

} // namespace proton

