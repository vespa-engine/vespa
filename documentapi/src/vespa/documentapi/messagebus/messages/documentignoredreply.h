// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentreply.h"

namespace documentapi {

class DocumentIgnoredReply : public DocumentReply {
public:
    typedef std::unique_ptr<DocumentIgnoredReply> UP;
    typedef std::shared_ptr<DocumentIgnoredReply> SP;

    DocumentIgnoredReply();

    string toString() const override { return "DocumentIgnoredReply"; }
};

}
