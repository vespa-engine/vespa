// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "userdocumentsbuilder.h"

namespace proton::test {

UserDocumentsBuilder::UserDocumentsBuilder()
    : _builder(),
      _docs()
{
}

UserDocumentsBuilder::~UserDocumentsBuilder() = default;

UserDocumentsBuilder &
UserDocumentsBuilder::createDoc(uint32_t userId, search::DocumentIdT lid)
{
    vespalib::string docId = vespalib::make_string("id:test:searchdocument:n=%u:%u", userId, lid);
    document::Document::SP doc(_builder.make_document(docId));
    _docs.addDoc(userId, Document(doc, lid, storage::spi::Timestamp(lid)));
    return *this;
}


UserDocumentsBuilder &
UserDocumentsBuilder::createDocs(uint32_t userId,
                                 search::DocumentIdT begin,
                                 search::DocumentIdT end)
{
    for (search::DocumentIdT lid = begin; lid < end; ++lid) {
        createDoc(userId, lid);
    }
    return *this;
}

}
