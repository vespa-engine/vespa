// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

DocumentReply::UP
GetDocumentMessage::doCreateReply() const
{
    return DocumentReply::UP(new GetDocumentReply());
}

bool
GetDocumentMessage::hasSequenceId() const
{
    return true;
}

uint64_t
GetDocumentMessage::getSequenceId() const
{
    return *reinterpret_cast<const uint64_t*>(_documentId.getGlobalId().get());
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
