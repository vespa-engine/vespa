// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.test.userdocumentsbuilder");

#include "userdocumentsbuilder.h"

namespace proton {

namespace test {

UserDocumentsBuilder::UserDocumentsBuilder()
    : _schema(),
      _builder(_schema),
      _docs()
{
}


UserDocumentsBuilder &
UserDocumentsBuilder::createDoc(uint32_t userId, search::DocumentIdT lid)
{
    vespalib::string docId = vespalib::make_string("userdoc:test:%u:%u", userId, lid);
    document::Document::SP doc(_builder.startDocument(docId).endDocument().release());
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

} // namespace test

} // namespace proton
