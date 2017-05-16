// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/repo/documenttyperepo.h>

namespace document {

class TestDocRepo {
    DocumenttypesConfig _cfg;
    DocumentTypeRepo::SP _repo;

public:
    TestDocRepo();
    ~TestDocRepo();

    static DocumenttypesConfig getDefaultConfig();

    const DocumentTypeRepo& getTypeRepo() const { return *_repo; }
    const DocumentTypeRepo::SP getTypeRepoSp() const { return _repo; }
    const DocumenttypesConfig& getTypeConfig() const { return _cfg; }
    const DataType* getDocumentType(const vespalib::string &name) const;
};

DocumenttypesConfig readDocumenttypesConfig(const char *file_name);
DocumenttypesConfig readDocumenttypesConfig(const std::string& file_name);

}  // namespace document

