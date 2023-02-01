// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryresultmessage.h"

namespace documentapi {

QueryResultMessage::QueryResultMessage() = default;

QueryResultMessage::QueryResultMessage(vdslib::SearchResult && result, const vdslib::DocumentSummary & summary) :
    VisitorMessage(),
    _searchResult(std::move(result)),
    _summary(summary)
{}

QueryResultMessage::~QueryResultMessage() = default;

DocumentReply::UP
QueryResultMessage::doCreateReply() const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_QUERYRESULT);
}

uint32_t
QueryResultMessage::getApproxSize() const
{
    return getSearchResult().getSerializedSize() + getDocumentSummary().getSerializedSize();
}

uint32_t
QueryResultMessage::getType() const
{
    return DocumentProtocol::MESSAGE_QUERYRESULT;
}

}
