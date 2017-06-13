// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "putdocumentmessage.h"
#include "writedocumentreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/vespalib/util/exceptions.h>

namespace documentapi {

PutDocumentMessage::PutDocumentMessage() :
    TestAndSetMessage(),
    _document(),
    _time(0)
{}

PutDocumentMessage::PutDocumentMessage(document::Document::SP document) :
    TestAndSetMessage(),
    _document(),
    _time(0)
{
    setDocument(document);
}

PutDocumentMessage::~PutDocumentMessage() {}

DocumentReply::UP
PutDocumentMessage::doCreateReply() const
{
    return DocumentReply::UP(new WriteDocumentReply(DocumentProtocol::REPLY_PUTDOCUMENT));
}

bool
PutDocumentMessage::hasSequenceId() const
{
    return true;
}

uint64_t
PutDocumentMessage::getSequenceId() const
{
    return *reinterpret_cast<const uint64_t*>(_document->getId().getGlobalId().get());
}

uint32_t
PutDocumentMessage::getType() const
{
    return DocumentProtocol::MESSAGE_PUTDOCUMENT;
}

document::Document::SP
PutDocumentMessage::getDocument()
{
    return _document;
}

std::shared_ptr<const document::Document>
PutDocumentMessage::getDocument() const
{
    return _document;
}

void
PutDocumentMessage::setDocument(document::Document::SP document)
{
    if (document.get() == NULL) {
        throw vespalib::IllegalArgumentException("Document can not be null.", VESPA_STRLOC);
    }
    _document = document;
}

}

