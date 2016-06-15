// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/closure.h>
#include <vespa/document/config/config-documenttypes.h>

namespace document {
class AnnotationType;
class DataType;
class DataTypeRepo;
class DocumentType;

class DocumentTypeRepo {
    typedef vespalib::hash_map<int32_t, DataTypeRepo *> DocumentTypeMap;

    DocumentTypeMap _doc_types;

public:
    typedef std::shared_ptr<DocumentTypeRepo> SP;
    typedef std::unique_ptr<DocumentTypeRepo> UP;

    // This one should only be used for testing. If you do not have any config.
    explicit DocumentTypeRepo(const DocumentType & docType);

    DocumentTypeRepo();
    explicit DocumentTypeRepo(const DocumenttypesConfig & config);
    ~DocumentTypeRepo();

    const DocumentType *getDocumentType(int32_t doc_type_id) const;
    const DocumentType *getDocumentType(const vespalib::stringref &name) const;
    const DataType *getDataType(const DocumentType &doc_type, int32_t id) const;
    const DataType *getDataType(const DocumentType &doc_type,
                                const vespalib::stringref &name) const;
    const AnnotationType *getAnnotationType(const DocumentType &doc_type,
                                            int32_t id) const;
    void forEachDocumentType(
            vespalib::Closure1<const DocumentType &> &c) const;

private:
    DocumentTypeRepo(const DocumentTypeRepo &);
    DocumentTypeRepo &operator=(const DocumentTypeRepo &);
};

DocumenttypesConfig readDocumenttypesConfig(const char *file_name);

}  // namespace document

