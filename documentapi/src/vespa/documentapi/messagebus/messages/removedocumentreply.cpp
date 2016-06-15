// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/removedocumentreply.h>

namespace documentapi {

RemoveDocumentReply::RemoveDocumentReply() :
    WriteDocumentReply(DocumentProtocol::REPLY_REMOVEDOCUMENT),
    _found(true)
{
    // empty
}

}
