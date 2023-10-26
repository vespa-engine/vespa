// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "updatedocumentreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

UpdateDocumentReply::UpdateDocumentReply() :
    WriteDocumentReply(DocumentProtocol::REPLY_UPDATEDOCUMENT),
    _found(true)
{}

}
