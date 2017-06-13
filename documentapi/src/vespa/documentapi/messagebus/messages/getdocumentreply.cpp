// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getdocumentreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

GetDocumentReply::GetDocumentReply() :
    DocumentAcceptedReply(DocumentProtocol::REPLY_GETDOCUMENT),
    _document(),
    _lastModified(0)
{}

GetDocumentReply::~GetDocumentReply() {}

GetDocumentReply::GetDocumentReply(document::Document::SP document) :
    DocumentAcceptedReply(DocumentProtocol::REPLY_GETDOCUMENT),
    _document(document),
    _lastModified(0)
{
    if (_document.get()) {
        _lastModified = _document->getLastModified();
    }
}

document::Document::SP
GetDocumentReply::getDocument()
{
    return _document;
}

std::shared_ptr<const document::Document>
GetDocumentReply::getDocument() const
{
    return _document;
}

void
GetDocumentReply::setDocument(document::Document::SP document)
{
    _document = document;
    if (document.get()) {
        _lastModified = document->getLastModified();
    } else {
        _lastModified = 0u;
    }
}

void
GetDocumentReply::setLastModified(uint64_t lastModified)
{
    _lastModified = lastModified;
}

}
