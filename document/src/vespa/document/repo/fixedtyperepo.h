// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documenttyperepo.h"

namespace document {

// Combines a DocumentTypeRepo and a DocumentType to allow easy access
// to the types contained in the DocumentType's namespace.
class FixedTypeRepo {
    const DocumentTypeRepo *_repo;
    const DocumentType *_doc_type;

public:
    explicit FixedTypeRepo(const DocumentTypeRepo &repo)
        : _repo(&repo), _doc_type(repo.getDefaultDocType()) {}
    FixedTypeRepo(const DocumentTypeRepo &repo, const DocumentType &doc_type)
        : _repo(&repo), _doc_type(&doc_type) {}
    FixedTypeRepo(const DocumentTypeRepo &repo, const vespalib::string &type);

    const DataType *getDataType(int32_t id) const { return _repo->getDataType(*_doc_type, id); }
    const DataType *getDataType(const vespalib::string &name) const { return _repo->getDataType(*_doc_type, name); }
    const AnnotationType *getAnnotationType(int32_t id) const { return _repo->getAnnotationType(*_doc_type, id); }
    const DocumentTypeRepo &getDocumentTypeRepo() const { return *_repo; }
    const DocumentType &getDocumentType() const { return *_doc_type; }
};

}  // namespace document

