// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "removedocumentmessage.h"
#include "removedocumentreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/vespalib/util/memory.h>

namespace documentapi {

RemoveDocumentMessage::RemoveDocumentMessage() :
    TestAndSetMessage(),
    _documentId()
{
}

RemoveDocumentMessage::RemoveDocumentMessage(const document::DocumentId& documentId) :
    TestAndSetMessage(),
    _documentId(documentId)
{
}

RemoveDocumentMessage::~RemoveDocumentMessage() {
}

DocumentReply::UP
RemoveDocumentMessage::doCreateReply() const
{
    return DocumentReply::UP(new RemoveDocumentReply());
}

bool
RemoveDocumentMessage::hasSequenceId() const
{
    return true;
}

uint64_t
RemoveDocumentMessage::getSequenceId() const
{
    return vespalib::Unaligned<uint64_t>::at(_documentId.getGlobalId().get()).read();
}

uint32_t
RemoveDocumentMessage::getType() const
{
    return DocumentProtocol::MESSAGE_REMOVEDOCUMENT;
}

const document::DocumentId&
RemoveDocumentMessage::getDocumentId() const
{
    return _documentId;
}

void
RemoveDocumentMessage::setDocumentId(const document::DocumentId& documentId)
{
    _documentId = documentId;
}

}
