// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "newconfigoperation.h"
#include <vespa/vespalib/util/stringfmt.h>

using document::DocumentTypeRepo;
using vespalib::make_string;

namespace proton {

NewConfigOperation::NewConfigOperation(SerialNum serialNum,
                                       IStreamHandler &streamHandler)
    : FeedOperation(FeedOperation::NEW_CONFIG),
      _streamHandler(streamHandler)
{
    setSerialNum(serialNum);
}


void
NewConfigOperation::serialize(vespalib::nbostream &os) const
{
    _streamHandler.serializeConfig(getSerialNum(), os);
}


void
NewConfigOperation::deserialize(vespalib::nbostream &is,
                                const DocumentTypeRepo &)
{
    _streamHandler.deserializeConfig(getSerialNum(), is);
}

vespalib::string NewConfigOperation::toString() const {
    return make_string("NewConfig(serialNum=%" PRIu64 ")", getSerialNum());
}

} // namespace proton
