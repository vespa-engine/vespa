// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getdocumentmessage.h"
#include "getdocumentreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/document/fieldset/fieldsets.h>

namespace documentapi {

GetDocumentMessage::GetDocumentMessage() :
    DocumentMessage(),
    _documentId(),
    _fieldSet(document::AllFields::NAME)
{}

GetDocumentMessage::GetDocumentMessage(const document::DocumentId &documentId) :
    DocumentMessage(),
    _documentId(documentId),
    _fieldSet(document::AllFields::NAME)
{
}

GetDocumentMessage::GetDocumentMessage(const document::DocumentId &documentId,
                                       vespalib::stringref fieldSet) :
    DocumentMessage(),
    _documentId(documentId),
    _fieldSet(fieldSet)
{
}

GetDocumentMessage::~GetDocumentMessage() = default;

DocumentReply::UP
GetDocumentMessage::doCreateReply() const
{
    return std::make_unique<GetDocumentReply>();
}

uint32_t
GetDocumentMessage::getType() const
{
    return DocumentProtocol::MESSAGE_GETDOCUMENT;
}

const document::DocumentId &
GetDocumentMessage::getDocumentId() const
{
    return _documentId;
}

void
GetDocumentMessage::setDocumentId(const document::DocumentId &documentId)
{
    _documentId = documentId;
}

}
