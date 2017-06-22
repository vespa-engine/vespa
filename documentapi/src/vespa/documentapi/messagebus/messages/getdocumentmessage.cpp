// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getdocumentmessage.h"
#include "getdocumentreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

GetDocumentMessage::GetDocumentMessage() :
    DocumentMessage(),
    _documentId(),
    _fieldSet("[all]")
{}

GetDocumentMessage::GetDocumentMessage(const document::DocumentId &documentId, int flags) :
    DocumentMessage(),
    _documentId(documentId)
{
    setFlags(flags);
}

GetDocumentMessage::GetDocumentMessage(const document::DocumentId &documentId,
                                       const vespalib::stringref & fieldSet) :
    DocumentMessage(),
    _documentId(documentId),
    _fieldSet(fieldSet)
{
}

GetDocumentMessage::~GetDocumentMessage() {
}

DocumentReply::UP
GetDocumentMessage::doCreateReply() const
{
    return DocumentReply::UP(new GetDocumentReply());
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
