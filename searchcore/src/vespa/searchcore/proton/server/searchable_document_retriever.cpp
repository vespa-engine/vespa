// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "searchable_document_retriever.h"

namespace proton {

SearchableDocumentRetriever::SearchableDocumentRetriever(
        const SearchableFeedView::SP &fw, const SearchView::SP &sv)
    : DocumentRetriever(fw->getPersistentParams()._docTypeName,
                        *fw->getDocumentTypeRepo(),
                        *fw->getSchema(),
                        *sv->getDocumentMetaStore(),
                        *sv->getAttributeManager(),
                        fw->getDocumentStore()),
      feedView(fw)
{
}

}  // namespace proton
