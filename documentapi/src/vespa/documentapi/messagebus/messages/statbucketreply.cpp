// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "statbucketreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

StatBucketReply::StatBucketReply() :
    DocumentReply(DocumentProtocol::REPLY_STATBUCKET),
    _results()
{
    // empty
}

}
