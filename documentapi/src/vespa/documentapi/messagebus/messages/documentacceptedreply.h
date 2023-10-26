// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentreply.h"

namespace documentapi {

/**
 * Common base class for replies that indicate that a document was routed
 * to some recipient. Does not imply that the reply contains no errors!
 */
class DocumentAcceptedReply : public DocumentReply {
public:
    DocumentAcceptedReply(uint32_t type)
        : DocumentReply(type)
    {}
};

} // ns documentapi

