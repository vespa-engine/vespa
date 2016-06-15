// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/wrongdistributionreply.h>

namespace documentapi {

WrongDistributionReply::WrongDistributionReply() :
    DocumentReply(DocumentProtocol::REPLY_WRONGDISTRIBUTION),
    _systemState()
{
    // empty
}

WrongDistributionReply::WrongDistributionReply(const string &systemState) :
    DocumentReply(DocumentProtocol::REPLY_WRONGDISTRIBUTION),
    _systemState(systemState)
{
    // empty
}

}
