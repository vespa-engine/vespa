// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentsummarymessage.h"

using vdslib::DocumentSummary;

namespace documentapi {

DocumentSummaryMessage::DocumentSummaryMessage(const DocumentSummary & sr) :
    VisitorMessage(),
    DocumentSummary(sr)
{
    // empty
}

DocumentSummaryMessage::DocumentSummaryMessage() :
    VisitorMessage(),
    DocumentSummary()
{
    // empty
}

DocumentReply::UP
DocumentSummaryMessage::doCreateReply() const
{
    return DocumentReply::UP(new VisitorReply(DocumentProtocol::REPLY_DOCUMENTSUMMARY));
}

uint32_t
DocumentSummaryMessage::getApproxSize() const
{
    return DocumentSummary::getSerializedSize();
}

uint32_t
DocumentSummaryMessage::getType() const
{
    return DocumentProtocol::MESSAGE_DOCUMENTSUMMARY;
}

}

