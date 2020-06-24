// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryresultmessage.h"

namespace documentapi {

QueryResultMessage::QueryResultMessage() = default;

QueryResultMessage::QueryResultMessage(const vdslib::SearchResult & result, const vdslib::DocumentSummary & summary) :
    VisitorMessage(),
    _searchResult(result),
    _summary(summary)
{}

QueryResultMessage::~QueryResultMessage() = default;

DocumentReply::UP
QueryResultMessage::doCreateReply() const
{
    return DocumentReply::UP(new VisitorReply(DocumentProtocol::REPLY_QUERYRESULT));
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
