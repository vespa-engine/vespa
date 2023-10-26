// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "emptybucketsmessage.h"

namespace documentapi {

EmptyBucketsMessage::EmptyBucketsMessage() :
    _bucketIds()
{
}

EmptyBucketsMessage::EmptyBucketsMessage(const std::vector<document::BucketId> &bucketIds) :
    _bucketIds(bucketIds)
{
}

EmptyBucketsMessage::~EmptyBucketsMessage() {
}

void
EmptyBucketsMessage::setBucketIds(const std::vector<document::BucketId> &bucketIds)
{
    _bucketIds = bucketIds;
}

void
EmptyBucketsMessage::resize(uint32_t size)
{
    _bucketIds.resize(size);
}

DocumentReply::UP
EmptyBucketsMessage::doCreateReply() const
{
    return DocumentReply::UP(new VisitorReply(DocumentProtocol::REPLY_EMPTYBUCKETS));
}

uint32_t
EmptyBucketsMessage::getType() const
{
    return DocumentProtocol::MESSAGE_EMPTYBUCKETS;
}

}
