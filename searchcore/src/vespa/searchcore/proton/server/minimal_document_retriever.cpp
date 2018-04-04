// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "minimal_document_retriever.h"
#include <vespa/searchlib/docstore/idocumentstore.h>

using document::Document;
using document::DocumentTypeRepo;
using search::DocumentIdT;
using search::IDocumentStore;

namespace proton {

MinimalDocumentRetriever::MinimalDocumentRetriever(
        const DocTypeName &docTypeName,
        const std::shared_ptr<const DocumentTypeRepo> repo,
        const IDocumentMetaStoreContext &meta_store,
        const IDocumentStore &doc_store,
        bool hasFields)
    : DocumentRetrieverBase(docTypeName, *repo, meta_store, hasFields),
      _repo(repo),
      _doc_store(doc_store) {
}

Document::UP MinimalDocumentRetriever::getDocument(DocumentIdT lid) const {
    return _doc_store.read(lid, *_repo);
}

void MinimalDocumentRetriever::visitDocuments(const LidVector & lids, search::IDocumentVisitor & visitor, ReadConsistency) const {
    _doc_store.visit(lids, getDocumentTypeRepo(), visitor);
}

}  // namespace proton
