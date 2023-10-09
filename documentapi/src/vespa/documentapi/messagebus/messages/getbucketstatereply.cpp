// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "getbucketstatereply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

GetBucketStateReply::GetBucketStateReply() :
    DocumentReply(DocumentProtocol::REPLY_GETBUCKETSTATE),
    _state()
{ }

GetBucketStateReply::GetBucketStateReply(std::vector<DocumentState> &state) :
    DocumentReply(DocumentProtocol::REPLY_GETBUCKETSTATE),
    _state()
{
    _state.swap(state);
}

GetBucketStateReply::~GetBucketStateReply() { }

}
