// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "userdocuments.h"
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace proton::test {

/**
 * Builder for creating documents for a set of users.
 */
class UserDocumentsBuilder
{
private:
    search::index::Schema     _schema;
    search::index::DocBuilder _builder;
    UserDocuments             _docs;
public:
    UserDocumentsBuilder();
    ~UserDocumentsBuilder();
    const std::shared_ptr<const document::DocumentTypeRepo> &getRepo() const {
        return _builder.getDocumentTypeRepo();
    }
    UserDocumentsBuilder &createDoc(uint32_t userId, search::DocumentIdT lid);
    UserDocumentsBuilder &createDocs(uint32_t userId, search::DocumentIdT begin,
                                     search::DocumentIdT end);
    UserDocumentsBuilder &clearDocs() {
        _docs.clear();
        return *this;
    }
    const UserDocuments &getDocs() const { return _docs; }
};


}
