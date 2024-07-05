// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wrongdistributionreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

WrongDistributionReply::WrongDistributionReply() :
    DocumentReply(DocumentProtocol::REPLY_WRONGDISTRIBUTION),
    _systemState()
{}

WrongDistributionReply::WrongDistributionReply(const string &systemState) :
    DocumentReply(DocumentProtocol::REPLY_WRONGDISTRIBUTION),
    _systemState(systemState)
{}

WrongDistributionReply::~WrongDistributionReply() {}

}
