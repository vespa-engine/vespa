// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "statbucketmessage.h"
#include "statbucketreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

using document::BucketSpace;

namespace documentapi {

StatBucketMessage::StatBucketMessage() :
    DocumentMessage(),
    _bucket(BucketSpace::placeHolder(), document::BucketId()),
    _documentSelection()
{}

StatBucketMessage::StatBucketMessage(document::Bucket bucket, const string& documentSelection) :
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

uint32_t
StatBucketMessage::getType() const
{
    return DocumentProtocol::MESSAGE_STATBUCKET;
}

}
