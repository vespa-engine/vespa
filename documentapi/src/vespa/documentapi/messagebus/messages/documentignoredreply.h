// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentreply.h"

namespace documentapi {

class DocumentIgnoredReply : public DocumentReply {
public:
    using UP = std::unique_ptr<DocumentIgnoredReply>;
    using SP = std::shared_ptr<DocumentIgnoredReply>;

    DocumentIgnoredReply();

    string toString() const override { return "DocumentIgnoredReply"; }
};

}
