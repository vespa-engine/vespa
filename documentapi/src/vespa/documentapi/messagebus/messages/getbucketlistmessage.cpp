// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getbucketlistmessage.h"
#include "getbucketlistreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

GetBucketListMessage::GetBucketListMessage() :
    DocumentMessage(),
    _bucketId()
{
    // empty
}

GetBucketListMessage::GetBucketListMessage(const document::BucketId &bucketId) :
    DocumentMessage(),
    _bucketId(bucketId)
{
    // empty
}

DocumentReply::UP
GetBucketListMessage::doCreateReply() const
{
    return DocumentReply::UP(new GetBucketListReply());
}

bool
GetBucketListMessage::hasSequenceId() const
{
    return true;
}

uint64_t
GetBucketListMessage::getSequenceId() const
{
    return _bucketId.getRawId();
}

uint32_t
GetBucketListMessage::getType() const
{
    return DocumentProtocol::MESSAGE_GETBUCKETLIST;
}

}
