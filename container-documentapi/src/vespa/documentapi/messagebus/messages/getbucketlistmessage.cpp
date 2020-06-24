// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getbucketlistmessage.h"
#include "getbucketlistreply.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>

using document::FixedBucketSpaces;

namespace documentapi {

GetBucketListMessage::GetBucketListMessage(const document::BucketId &bucketId) :
    DocumentMessage(),
    _bucketId(bucketId),
    _bucketSpace(FixedBucketSpaces::default_space_name())
{
}

GetBucketListMessage::~GetBucketListMessage() = default;

DocumentReply::UP
GetBucketListMessage::doCreateReply() const
{
    return DocumentReply::UP(new GetBucketListReply());
}

uint32_t
GetBucketListMessage::getType() const
{
    return DocumentProtocol::MESSAGE_GETBUCKETLIST;
}

}
