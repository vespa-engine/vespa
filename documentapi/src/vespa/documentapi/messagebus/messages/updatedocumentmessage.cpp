// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "updatedocumentmessage.h"
#include "updatedocumentreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/util/exceptions.h>

namespace documentapi {

UpdateDocumentMessage::UpdateDocumentMessage() :
    TestAndSetMessage(),
    _documentUpdate(),
    _oldTime(0),
    _newTime(0)
{}

UpdateDocumentMessage::UpdateDocumentMessage(document::DocumentUpdate::SP documentUpdate) :
    TestAndSetMessage(),
    _documentUpdate(),
    _oldTime(0),
    _newTime(0)
{
    setDocumentUpdate(std::move(documentUpdate));
}

UpdateDocumentMessage::~UpdateDocumentMessage() {}

DocumentReply::UP
UpdateDocumentMessage::doCreateReply() const
{
    return DocumentReply::UP(new UpdateDocumentReply());
}

bool
UpdateDocumentMessage::hasSequenceId() const
{
    return true;
}

uint64_t
UpdateDocumentMessage::getSequenceId() const
{
    return vespalib::Unaligned<uint64_t>::at(_documentUpdate->getId().getGlobalId().get()).read();
}

uint32_t
UpdateDocumentMessage::getType() const
{
    return DocumentProtocol::MESSAGE_UPDATEDOCUMENT;
}

void
UpdateDocumentMessage::setDocumentUpdate(document::DocumentUpdate::SP documentUpdate)
{
    if ( ! documentUpdate) {
        throw vespalib::IllegalArgumentException("Document update can not be null.", VESPA_STRLOC);
    }
    _documentUpdate = std::move(documentUpdate);
}

}
