// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/closure.h>

namespace document {

namespace internal {
    class InternalDocumenttypesType;
    class DocumentTypeMap;
}

class AnnotationType;
class DataType;
class DataTypeRepo;
class DocumentType;

class DocumentTypeRepo {
    std::unique_ptr<internal::DocumentTypeMap> _doc_types;

public:
    using DocumenttypesConfig = const internal::InternalDocumenttypesType;
    typedef std::unique_ptr<DocumentTypeRepo> UP;

    // This one should only be used for testing. If you do not have any config.
    explicit DocumentTypeRepo(const DocumentType & docType);

    DocumentTypeRepo(const DocumentTypeRepo &) = delete;
    DocumentTypeRepo &operator=(const DocumentTypeRepo &) = delete;
    DocumentTypeRepo();
    explicit DocumentTypeRepo(const DocumenttypesConfig & config);
    ~DocumentTypeRepo();

    const DocumentType *getDocumentType(int32_t doc_type_id) const;
    const DocumentType *getDocumentType(const vespalib::stringref &name) const;
    const DataType *getDataType(const DocumentType &doc_type, int32_t id) const;
    const DataType *getDataType(const DocumentType &doc_type, const vespalib::stringref &name) const;
    const AnnotationType *getAnnotationType(const DocumentType &doc_type, int32_t id) const;
    void forEachDocumentType(vespalib::Closure1<const DocumentType &> &c) const;

};

}  // namespace document

