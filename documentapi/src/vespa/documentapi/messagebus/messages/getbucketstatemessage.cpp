// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getbucketstatemessage.h"
#include "getbucketstatereply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

GetBucketStateMessage::GetBucketStateMessage() :
    DocumentMessage(),
    _bucket()
{ }

GetBucketStateMessage::GetBucketStateMessage(const document::BucketId &bucket) :
    DocumentMessage(),
    _bucket(bucket)
{ }

DocumentReply::UP
GetBucketStateMessage::doCreateReply() const
{
    return DocumentReply::UP(new GetBucketStateReply());
}

bool
GetBucketStateMessage::hasSequenceId() const
{
    return true;
}

uint64_t
GetBucketStateMessage::getSequenceId() const
{
    return _bucket.getRawId();
}

uint32_t
GetBucketStateMessage::getType() const
{
    return DocumentProtocol::MESSAGE_GETBUCKETSTATE;
}

}
