// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/datatype/datatype.h>

namespace document {

class DocumentTypeRepo;

class TestDocRepo {
    DocumenttypesConfig _cfg;
    std::shared_ptr<const DocumentTypeRepo> _repo;

public:
    TestDocRepo();
    ~TestDocRepo();

    static DocumenttypesConfig getDefaultConfig();

    const DocumentTypeRepo& getTypeRepo() const { return *_repo; }
    std::shared_ptr<const DocumentTypeRepo> getTypeRepoSp() const { return _repo; }
    const DocumenttypesConfig& getTypeConfig() const { return _cfg; }
    const DataType* getDocumentType(const vespalib::string &name) const;
};

DocumenttypesConfig readDocumenttypesConfig(const char *file_name);
DocumenttypesConfig readDocumenttypesConfig(const std::string& file_name);

}  // namespace document

