// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operation.h"
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback");

namespace storage::distributor {

Operation::Operation()
    : _startTime()
{
}

Operation::~Operation() = default;

std::string
Operation::getStatus() const
{
    return vespalib::make_string("%s (started %s)", getName(), vespalib::to_string(_startTime).c_str());
}

void
Operation::start(DistributorStripeMessageSender& sender, vespalib::system_time startTime)
{
    _startTime = startTime;
    onStart(sender);
}

void
Operation::start(DistributorStripeMessageSender& sender)
{
    start(sender, vespalib::system_time());
}

void
Operation::copyMessageSettings(const api::StorageCommand& source, api::StorageCommand& target)
{
    target.getTrace().setLevel(source.getTrace().getLevel());
    target.setTimeout(source.getTimeout());
    target.setPriority(source.getPriority());
}

void
Operation::on_blocked()
{
}

void
Operation::on_throttled()
{
}

}

