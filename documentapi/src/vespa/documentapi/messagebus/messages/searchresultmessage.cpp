// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchresultmessage.h"

using vdslib::SearchResult;

namespace documentapi {

SearchResultMessage::SearchResultMessage() :
    VisitorMessage(),
    SearchResult()
{
    // empty
}

SearchResultMessage::SearchResultMessage(SearchResult &&result) :
    VisitorMessage(),
    SearchResult(std::move(result))
{
    // empty
}

DocumentReply::UP
SearchResultMessage::doCreateReply() const
{
    return std::make_unique<VisitorReply>(DocumentProtocol::REPLY_SEARCHRESULT);
}

uint32_t
SearchResultMessage::getApproxSize() const
{
    return SearchResult::getSerializedSize();
}

uint32_t
SearchResultMessage::getType() const
{
    return DocumentProtocol::MESSAGE_SEARCHRESULT;
}

}

