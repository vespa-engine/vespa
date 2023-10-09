// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "documentignoredreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

DocumentIgnoredReply::DocumentIgnoredReply()
    : DocumentReply(DocumentProtocol::REPLY_DOCUMENTIGNORED)
{
}

}
