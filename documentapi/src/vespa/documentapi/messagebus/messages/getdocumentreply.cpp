// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getdocumentreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/document/fieldvalue/document.h>

namespace documentapi {

GetDocumentReply::GetDocumentReply() :
    DocumentAcceptedReply(DocumentProtocol::REPLY_GETDOCUMENT),
    _document(),
    _lastModified(0)
{}

GetDocumentReply::~GetDocumentReply() {}

GetDocumentReply::GetDocumentReply(document::Document::SP document) :
    DocumentAcceptedReply(DocumentProtocol::REPLY_GETDOCUMENT),
    _document(std::move(document)),
    _lastModified(0)
{
    if (_document) {
        _lastModified = _document->getLastModified();
    }
}

void
GetDocumentReply::setDocument(document::Document::SP document)
{
    _document = std::move(document);
    if (document.get()) {
        _lastModified = document->getLastModified();
    } else {
        _lastModified = 0u;
    }
}

}
