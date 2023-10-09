// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "putdocumentmessage.h"
#include "writedocumentreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/document/fieldvalue/document.h>
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
    setDocument(std::move(document));
}

PutDocumentMessage::~PutDocumentMessage() = default;

DocumentReply::UP
PutDocumentMessage::doCreateReply() const
{
    return std::make_unique<WriteDocumentReply>(DocumentProtocol::REPLY_PUTDOCUMENT);
}

bool
PutDocumentMessage::hasSequenceId() const
{
    return true;
}

uint64_t
PutDocumentMessage::getSequenceId() const
{
    return vespalib::Unaligned<uint64_t>::at(_document->getId().getGlobalId().get()).read();
}

uint32_t
PutDocumentMessage::getType() const
{
    return DocumentProtocol::MESSAGE_PUTDOCUMENT;
}

void
PutDocumentMessage::setDocument(document::Document::SP document)
{
    if ( ! document ) {
        throw vespalib::IllegalArgumentException("Document can not be null.", VESPA_STRLOC);
    }
    _document = std::move(document);
}

}

