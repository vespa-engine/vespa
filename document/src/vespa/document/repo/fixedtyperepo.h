// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/datatype/datatype.h>

namespace document {

class DocumentTypeRepo;
class AnnotationType;

// Combines a DocumentTypeRepo and a DocumentType to allow easy access
// to the types contained in the DocumentType's namespace.
class FixedTypeRepo {
    const DocumentTypeRepo *_repo;
    const DocumentType *_doc_type;

public:
    explicit FixedTypeRepo(const DocumentTypeRepo &repo);
    FixedTypeRepo(const DocumentTypeRepo &repo, const DocumentType &doc_type)
        : _repo(&repo), _doc_type(&doc_type) {}
    FixedTypeRepo(const DocumentTypeRepo &repo, const vespalib::string &type);

    const DataType *getDataType(int32_t id) const;
    const DataType *getDataType(const vespalib::string &name) const;
    const AnnotationType *getAnnotationType(int32_t id) const;

    const DocumentTypeRepo &getDocumentTypeRepo() const { return *_repo; }
    const DocumentType &getDocumentType() const { return *_doc_type; }
};

}  // namespace document

