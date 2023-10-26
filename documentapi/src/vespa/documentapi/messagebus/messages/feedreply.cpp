// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedreply.h"

namespace documentapi {

FeedReply::FeedReply(uint32_t type) :
    DocumentReply(type),
    _feedAnswers()
{
    // empty
}

FeedReply::FeedReply(uint32_t type, const std::vector<FeedAnswer> &feedAnswers) :
    DocumentReply(type),
    _feedAnswers(feedAnswers)
{
    // empty
}

}
