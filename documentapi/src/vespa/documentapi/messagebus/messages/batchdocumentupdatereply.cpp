// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/batchdocumentupdatereply.h>
#include <vespa/documentapi/messagebus/messages/writedocumentreply.h>

namespace documentapi {

BatchDocumentUpdateReply::BatchDocumentUpdateReply()
    : WriteDocumentReply(DocumentProtocol::REPLY_BATCHDOCUMENTUPDATE)
{
}

}
