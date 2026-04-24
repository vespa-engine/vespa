// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "minimal_document_retriever.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchlib/docstore/idocumentstore.h>

using document::Document;
using document::DocumentTypeRepo;
using document::FieldCollection;
using document::FieldSet;
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
      _doc_store(doc_store),
      _field_count(repo->getDocumentType(docTypeName.getName())->getFieldCount()) {
}

MinimalDocumentRetriever::~MinimalDocumentRetriever() = default;

Document::UP
MinimalDocumentRetriever::getFullDocument(DocumentIdT lid) const {
    return _doc_store.read(lid, *_repo);
}

bool
MinimalDocumentRetriever::need_fetch_from_doc_store(const FieldSet& field_set) const {
    switch (field_set.getType()) {
        case FieldSet::Type::NONE:
        case FieldSet::Type::DOCID:
            return false;
        case FieldSet::Type::DOCUMENT_ONLY:
        case FieldSet::Type::ALL:
            return _field_count != 0;
        case FieldSet::Type::FIELD:
            return true;
        case FieldSet::Type::SET: {
            const auto &set = static_cast<const FieldCollection&>(field_set);
            return !set.getFields().empty();
        }
    }
    abort();
}

void
MinimalDocumentRetriever::visitDocuments(const LidVector & lids, search::IDocumentVisitor & visitor, ReadConsistency) const {
    _doc_store.visit(lids, getDocumentTypeRepo(), visitor);
}

}  // namespace proton
