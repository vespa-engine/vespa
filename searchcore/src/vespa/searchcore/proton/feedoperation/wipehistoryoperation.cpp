// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wipehistoryoperation.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace proton {

WipeHistoryOperation::WipeHistoryOperation()
    : FeedOperation(FeedOperation::WIPE_HISTORY),
      _wipeTimeLimit(0)
{
}

WipeHistoryOperation::WipeHistoryOperation(SerialNum serialNum,
                                           fastos::TimeStamp wipeTimeLimit)
    : FeedOperation(FeedOperation::WIPE_HISTORY),
      _wipeTimeLimit(wipeTimeLimit)
{
    setSerialNum(serialNum);
}

void WipeHistoryOperation::serialize(vespalib::nbostream &str) const {
    str << _wipeTimeLimit;
}
void WipeHistoryOperation::deserialize(vespalib::nbostream &str, const document::DocumentTypeRepo &) {
    fastos::TimeStamp::TimeT t;
    str >> t;
    _wipeTimeLimit = t;
}

vespalib::string WipeHistoryOperation::toString() const {
    return make_string("WipeHistory(wipeTimeLimit=%" PRIu64 ", serialNum=%" PRIu64 ")",
                       _wipeTimeLimit.ns(), getSerialNum());
}

} // namespace proton
