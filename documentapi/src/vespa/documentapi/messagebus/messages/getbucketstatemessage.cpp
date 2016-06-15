// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/getbucketstatemessage.h>
#include <vespa/documentapi/messagebus/messages/getbucketstatereply.h>

namespace documentapi {

GetBucketStateMessage::GetBucketStateMessage() :
    DocumentMessage(),
    _bucket()
{
    // empty
}

GetBucketStateMessage::GetBucketStateMessage(const document::BucketId &bucket) :
    DocumentMessage(),
    _bucket(bucket)
{
    // empty
}

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
