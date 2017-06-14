// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "statbucketmessage.h"
#include "statbucketreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

StatBucketMessage::StatBucketMessage() :
    DocumentMessage(),
    _bucket(),
    _documentSelection()
{}

StatBucketMessage::StatBucketMessage(document::BucketId bucket, const string& documentSelection) :
    DocumentMessage(),
    _bucket(bucket),
    _documentSelection(documentSelection)
{}

StatBucketMessage::~StatBucketMessage() {
}

DocumentReply::UP
StatBucketMessage::doCreateReply() const
{
    return DocumentReply::UP(new StatBucketReply());
}

bool
StatBucketMessage::hasSequenceId() const
{
    return true;
}

uint64_t
StatBucketMessage::getSequenceId() const
{
    return _bucket.getRawId();
}

uint32_t
StatBucketMessage::getType() const
{
    return DocumentProtocol::MESSAGE_STATBUCKET;
}

}
