// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <functional>
#include <memory>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/config/documenttypes_config_fwd.h>

namespace document {

namespace internal {
    class DocumentTypeMap;
}

class AnnotationType;
class DataType;
struct DataTypeRepo;
class DocumentType;

class DocumentTypeRepo {
public:
    // This one should only be used for testing. If you do not have any config.
    explicit DocumentTypeRepo(const DocumentType & docType);

    DocumentTypeRepo(const DocumentTypeRepo &) = delete;
    DocumentTypeRepo &operator=(const DocumentTypeRepo &) = delete;
    DocumentTypeRepo();
    explicit DocumentTypeRepo(const DocumenttypesConfig & config);
    ~DocumentTypeRepo();

    const DocumentType *getDocumentType(int32_t doc_type_id) const noexcept;
    const DocumentType *getDocumentType(vespalib::stringref name) const noexcept;
    const DataType *getDataType(const DocumentType &doc_type, int32_t id) const;
    const DataType *getDataType(const DocumentType &doc_type, vespalib::stringref name) const;
    const AnnotationType *getAnnotationType(const DocumentType &doc_type, int32_t id) const;
    void forEachDocumentType(std::function<void(const DocumentType &)> handler) const;
    const DocumentType *getDefaultDocType() const { return _default; }
private:
    std::unique_ptr<internal::DocumentTypeMap> _doc_types;
    const DocumentType * _default;

    DataTypeRepo * findRepo(int32_t doc_type_id) const;
};

}  // namespace document

