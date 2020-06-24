// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "statbucketmessage.h"
#include "statbucketreply.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>

using document::FixedBucketSpaces;

namespace documentapi {

StatBucketMessage::StatBucketMessage() :
    DocumentMessage(),
    _bucketId(document::BucketId()),
    _documentSelection(),
    _bucketSpace(FixedBucketSpaces::default_space_name())
{}

StatBucketMessage::StatBucketMessage(document::BucketId bucketId, const string& documentSelection) :
    DocumentMessage(),
    _bucketId(bucketId),
    _documentSelection(documentSelection),
    _bucketSpace(FixedBucketSpaces::default_space_name())
{}

StatBucketMessage::~StatBucketMessage() {
}

DocumentReply::UP
StatBucketMessage::doCreateReply() const
{
    return DocumentReply::UP(new StatBucketReply());
}

uint32_t
StatBucketMessage::getType() const
{
    return DocumentProtocol::MESSAGE_STATBUCKET;
}

}
